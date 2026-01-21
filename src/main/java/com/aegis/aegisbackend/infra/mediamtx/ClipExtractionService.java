package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 클립 추출 서비스
 * - MediaMTX HLS API를 통해 HTTP로 세그먼트 다운로드
 * - FFmpeg로 MP4 변환 후 MinIO에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipExtractionService {

    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final S3Service s3Service;
    private final WebClient.Builder webClientBuilder;

    @Value("${mediamtx.hls-url:http://localhost:8888}")
    private String hlsBaseUrl;

    @Value("${clip.extraction.temp-dir:/tmp/aegis-clips}")
    private String tempDir;

    @Value("${clip.extraction.segment-count:10}")
    private int defaultSegmentCount;

    // HLS 플레이리스트에서 세그먼트 파일명 추출용 패턴
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^([^#].+\\.ts)$", Pattern.MULTILINE);

    /**
     * 이벤트 클립 추출 (비동기)
     * - HLS 세그먼트 파일들을 HTTP로 다운로드하여 클립 생성
     * - MinIO에 저장 후 이벤트 clipUrl 업데이트
     *
     * 참고: @Transactional 제거 - 외부 I/O(HTTP, MinIO) 작업 위주이고,
     * @Async와 함께 사용 시 트랜잭션 컨텍스트가 전파되지 않음
     */
    @Async
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
     * - MediaMTX HLS API에서 m3u8 파싱 후 세그먼트 다운로드
     * - FFmpeg로 하나의 MP4로 합침
     * - MinIO에 업로드
     */
    public String extractAndSaveClip(UUID cameraId, UUID eventId, int segmentCount) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND_FOR_CLIP));

        String cameraName = camera.getName();
        Path tempDirPath = Path.of(tempDir, eventId.toString());

        try {
            // 임시 디렉토리 생성
            Files.createDirectories(tempDirPath);

            // HLS 세그먼트 다운로드 (HTTP)
            List<Path> segmentFiles = downloadHlsSegments(cameraName, tempDirPath, segmentCount);
            if (segmentFiles.isEmpty()) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED, "HLS 세그먼트를 다운로드할 수 없습니다: " + cameraName);
            }

            log.info("클립 추출 시작: camera={}, segments={}", cameraName, segmentFiles.size());

            // FFmpeg concat 리스트 파일 생성
            Path concatListPath = tempDirPath.resolve("concat.txt");
            createConcatList(concatListPath, segmentFiles);

            // 출력 파일 경로
            Path outputPath = tempDirPath.resolve("clip.mp4");

            // FFmpeg로 세그먼트 합치기
            boolean success = mergeSegmentsWithFFmpeg(concatListPath, outputPath);
            if (!success) {
                throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED, "FFmpeg 클립 합치기 실패");
            }

            // MinIO에 업로드
            byte[] clipData = Files.readAllBytes(outputPath);
            String clipKey = s3Service.uploadEventClip(eventId, clipData, "video/mp4");

            log.info("클립 저장 완료: camera={}, event={}, size={}KB, segments={}",
                    cameraName, eventId, clipData.length / 1024, segmentFiles.size());

            return clipKey;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("클립 추출/저장 실패: camera={}, event={}", cameraName, eventId, e);
            throw new BusinessException(ErrorCode.CLIP_EXTRACTION_FAILED);
        } finally {
            // 임시 파일 정리
            cleanupTempFiles(tempDirPath);
        }
    }

    /**
     * MediaMTX HLS API에서 세그먼트 다운로드 (HTTP)
     */
    private List<Path> downloadHlsSegments(String cameraName, Path tempDirPath, int segmentCount) {
        List<Path> downloadedFiles = new ArrayList<>();
        WebClient client = webClientBuilder.build();

        try {
            // 1. m3u8 플레이리스트 가져오기
            String playlistUrl = hlsBaseUrl + "/" + cameraName + "/index.m3u8";
            String playlist = client.get()
                    .uri(playlistUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (playlist == null || playlist.isEmpty()) {
                log.warn("HLS 플레이리스트가 비어있음: {}", playlistUrl);
                return downloadedFiles;
            }

            // 2. 세그먼트 파일명 추출
            List<String> segmentNames = parseSegmentNames(playlist);
            if (segmentNames.isEmpty()) {
                log.warn("HLS 플레이리스트에서 세그먼트를 찾을 수 없음: {}", playlistUrl);
                return downloadedFiles;
            }

            // 3. 최신 N개 세그먼트만 선택
            int startIndex = Math.max(0, segmentNames.size() - segmentCount);
            List<String> targetSegments = segmentNames.subList(startIndex, segmentNames.size());

            log.debug("세그먼트 다운로드 대상: {} / {} 개", targetSegments.size(), segmentNames.size());

            // 4. 각 세그먼트 다운로드
            for (int i = 0; i < targetSegments.size(); i++) {
                String segmentName = targetSegments.get(i);
                String segmentUrl = hlsBaseUrl + "/" + cameraName + "/" + segmentName;

                try {
                    byte[] segmentData = client.get()
                            .uri(segmentUrl)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .block();

                    if (segmentData != null && segmentData.length > 0) {
                        // 순서 보장을 위해 번호 붙여서 저장
                        Path segmentPath = tempDirPath.resolve(String.format("%03d_%s", i, segmentName));
                        Files.write(segmentPath, segmentData);
                        downloadedFiles.add(segmentPath);
                        log.debug("세그먼트 다운로드 완료: {}", segmentName);
                    }
                } catch (Exception e) {
                    log.warn("세그먼트 다운로드 실패: {}, error={}", segmentUrl, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("HLS 세그먼트 다운로드 실패: camera={}, error={}", cameraName, e.getMessage());
        }

        return downloadedFiles;
    }

    /**
     * m3u8 플레이리스트에서 세그먼트 파일명 추출
     */
    private List<String> parseSegmentNames(String playlist) {
        List<String> segments = new ArrayList<>();
        Matcher matcher = SEGMENT_PATTERN.matcher(playlist);
        while (matcher.find()) {
            segments.add(matcher.group(1));
        }
        return segments;
    }

    /**
     * FFmpeg concat demuxer용 리스트 파일 생성
     */
    private void createConcatList(Path listPath, List<Path> segmentFiles) throws Exception {
        try (FileWriter writer = new FileWriter(listPath.toFile())) {
            for (Path segment : segmentFiles) {
                // FFmpeg concat demuxer 형식: file '/path/to/file.ts'
                writer.write("file '" + segment.toAbsolutePath() + "'\n");
            }
        }
        log.debug("Concat 리스트 생성: {}, files={}", listPath, segmentFiles.size());
    }

    /**
     * FFmpeg로 세그먼트 파일 합치기
     */
    private boolean mergeSegmentsWithFFmpeg(Path concatListPath, Path outputPath) {
        try {
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
            if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
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
     * 임시 디렉토리 및 파일 정리
     */
    private void cleanupTempFiles(Path tempDirPath) {
        try {
            if (Files.exists(tempDirPath)) {
                Files.walk(tempDirPath)
                        .sorted((a, b) -> -a.compareTo(b))  // 역순 정렬 (파일 먼저, 디렉토리 나중에)
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception e) {
                                log.warn("임시 파일 삭제 실패: {}", path);
                            }
                        });
            }
        } catch (Exception e) {
            log.warn("임시 디렉토리 정리 실패: {}", tempDirPath);
        }
    }
}
