package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 클립 추출 서비스
 * - MediaMTX HLS 녹화 파일(.ts)을 가져와서 하나로 합침
 * - MinIO에 클립 저장 후 이벤트에 URL 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipExtractionService {

    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final S3Service s3Service;

    @Value("${mediamtx.recordings-dir:/recordings}")
    private String recordingsDir;

    @Value("${clip.extraction.temp-dir:/tmp/aegis-clips}")
    private String tempDir;

    @Value("${clip.extraction.segment-count:10}")
    private int defaultSegmentCount;

    /**
     * 이벤트 클립 추출 (비동기)
     * - HLS 세그먼트 파일들을 합쳐서 클립 생성
     * - MinIO에 저장 후 이벤트 clipUrl 업데이트
     */
    @Async
    @Transactional
    public void extractAndSaveClipAsync(UUID cameraId, UUID eventId, int segmentCount) {
        try {
            String clipKey = extractAndSaveClip(cameraId, eventId, segmentCount);

            // 이벤트에 clipUrl 업데이트
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                event.setClipUrl(clipKey);
                event.setStatus(EventStatus.RESOLVED);
                eventRepository.save(event);
                log.info("이벤트 클립 URL 업데이트: eventId={}, clipKey={}", eventId, clipKey);
            }
        } catch (Exception e) {
            log.error("클립 추출 실패: eventId={}", eventId, e);

            // 실패 시에도 이벤트 상태 업데이트
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                event.setStatus(EventStatus.RESOLVED);
                eventRepository.save(event);
            }
        }
    }

    /**
     * 기본 세그먼트 수로 클립 추출
     */
    @Async
    @Transactional
    public void extractAndSaveClipAsync(UUID cameraId, UUID eventId) {
        extractAndSaveClipAsync(cameraId, eventId, defaultSegmentCount);
    }

    /**
     * 이벤트 클립 추출 (동기)
     * - 카메라의 HLS 녹화 디렉토리에서 .ts 파일들을 가져옴
     * - FFmpeg로 하나의 MP4로 합침
     * - MinIO에 업로드
     */
    public String extractAndSaveClip(UUID cameraId, UUID eventId, int segmentCount) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new RuntimeException("카메라를 찾을 수 없습니다: " + cameraId));

        String cameraName = camera.getName();
        Path hlsDir = Path.of(recordingsDir, cameraName);
        Path tempDirPath = Path.of(tempDir);

        try {
            // 임시 디렉토리 생성
            Files.createDirectories(tempDirPath);

            // HLS 세그먼트 파일 목록 가져오기 (최신 N개)
            List<File> segmentFiles = getLatestSegments(hlsDir, segmentCount);
            if (segmentFiles.isEmpty()) {
                throw new RuntimeException("HLS 세그먼트 파일을 찾을 수 없습니다: " + hlsDir);
            }

            log.info("클립 추출 시작: camera={}, segments={}", cameraName, segmentFiles.size());

            // FFmpeg concat 리스트 파일 생성
            Path concatListPath = tempDirPath.resolve(eventId + "_concat.txt");
            createConcatList(concatListPath, segmentFiles);

            // 출력 파일 경로
            Path outputPath = tempDirPath.resolve(eventId + ".mp4");

            // FFmpeg로 세그먼트 합치기
            boolean success = mergeSegmentsWithFFmpeg(concatListPath, outputPath);
            if (!success) {
                throw new RuntimeException("FFmpeg 클립 합치기 실패");
            }

            // MinIO에 업로드
            byte[] clipData = Files.readAllBytes(outputPath);
            String clipKey = s3Service.uploadEventClip(eventId, clipData, "video/mp4");

            log.info("클립 저장 완료: camera={}, event={}, size={}KB, segments={}",
                    cameraName, eventId, clipData.length / 1024, segmentFiles.size());

            return clipKey;

        } catch (Exception e) {
            log.error("클립 추출/저장 실패: camera={}, event={}", cameraName, eventId, e);
            throw new RuntimeException("클립 추출 실패", e);
        } finally {
            // 임시 파일 정리
            cleanupTempFiles(eventId);
        }
    }

    /**
     * 최신 HLS 세그먼트 파일 가져오기
     */
    private List<File> getLatestSegments(Path hlsDir, int count) {
        File dir = hlsDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("HLS 디렉토리가 존재하지 않음: {}", hlsDir);
            return List.of();
        }

        File[] tsFiles = dir.listFiles((d, name) -> name.endsWith(".ts"));
        if (tsFiles == null || tsFiles.length == 0) {
            log.warn("HLS 세그먼트 파일이 없음: {}", hlsDir);
            return List.of();
        }

        // 파일명 또는 수정 시간으로 정렬하여 최신 N개 선택
        return Arrays.stream(tsFiles)
                .sorted(Comparator.comparingLong(File::lastModified))
                .skip(Math.max(0, tsFiles.length - count))
                .collect(Collectors.toList());
    }

    /**
     * FFmpeg concat demuxer용 리스트 파일 생성
     */
    private void createConcatList(Path listPath, List<File> segmentFiles) throws Exception {
        try (FileWriter writer = new FileWriter(listPath.toFile())) {
            for (File segment : segmentFiles) {
                // FFmpeg concat demuxer 형식: file '/path/to/file.ts'
                writer.write("file '" + segment.getAbsolutePath() + "'\n");
            }
        }
        log.debug("Concat 리스트 생성: {}, files={}", listPath, segmentFiles.size());
    }

    /**
     * FFmpeg로 세그먼트 파일 합치기
     */
    private boolean mergeSegmentsWithFFmpeg(Path concatListPath, Path outputPath) {
        try {
            // FFmpeg concat demuxer 사용
            // -f concat: concat demuxer 사용
            // -safe 0: 절대 경로 허용
            // -c copy: 재인코딩 없이 복사 (빠름)
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", concatListPath.toString(),
                    "-c", "copy",
                    "-movflags", "+faststart",  // 웹 스트리밍 최적화
                    "-y",
                    outputPath.toString()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 로그 출력
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            // 타임아웃 (60초)
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg 타임아웃");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg 종료 코드: {}", exitCode);
                return false;
            }

            // 파일 생성 확인
            File outputFile = outputPath.toFile();
            if (!outputFile.exists() || outputFile.length() == 0) {
                log.error("클립 파일 생성 실패: {}", outputPath);
                return false;
            }

            log.info("세그먼트 합치기 완료: {}", outputPath);
            return true;

        } catch (Exception e) {
            log.error("FFmpeg 실행 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 임시 파일 정리
     */
    private void cleanupTempFiles(UUID eventId) {
        try {
            Path tempDirPath = Path.of(tempDir);
            Path concatListPath = tempDirPath.resolve(eventId + "_concat.txt");
            Path outputPath = tempDirPath.resolve(eventId + ".mp4");

            Files.deleteIfExists(concatListPath);
            Files.deleteIfExists(outputPath);
        } catch (Exception e) {
            log.warn("임시 파일 정리 실패: eventId={}", eventId);
        }
    }
}
