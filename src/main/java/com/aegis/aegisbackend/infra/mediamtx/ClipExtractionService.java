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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 클립 추출 서비스
 * - MediaMTX RTSP 스트림에서 FFmpeg로 클립 추출
 * - MinIO에 클립 저장 후 이벤트에 URL 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipExtractionService {

    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final S3Service s3Service;

    @Value("${mediamtx.rtsp-url:rtsp://localhost:8554}")
    private String rtspBaseUrl;

    @Value("${clip.extraction.duration:10}")
    private int defaultClipDuration;

    @Value("${clip.extraction.temp-dir:/tmp/aegis-clips}")
    private String tempDir;

    /**
     * 이벤트 클립 추출 (비동기)
     * - RTSP 스트림에서 지정 시간만큼 클립 추출
     * - MinIO에 저장 후 이벤트 clipUrl 업데이트
     */
    @Async
    @Transactional
    public void extractAndSaveClipAsync(UUID cameraId, UUID eventId, int durationSeconds) {
        try {
            String clipKey = extractAndSaveClip(cameraId, eventId, durationSeconds);

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

            // 실패 시 이벤트 상태 업데이트
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                event.setStatus(EventStatus.RESOLVED);
                eventRepository.save(event);
            }
        }
    }

    /**
     * 이벤트 클립 추출 (동기)
     */
    public String extractAndSaveClip(UUID cameraId, UUID eventId, int durationSeconds) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new RuntimeException("카메라를 찾을 수 없습니다: " + cameraId));

        // 임시 디렉토리 생성
        Path tempDirPath = Path.of(tempDir);
        try {
            Files.createDirectories(tempDirPath);
        } catch (Exception e) {
            throw new RuntimeException("임시 디렉토리 생성 실패", e);
        }

        // RTSP URL 및 출력 파일 경로
        String rtspUrl = rtspBaseUrl + "/" + camera.getName();
        Path outputPath = tempDirPath.resolve(eventId + ".mp4");

        try {
            // FFmpeg로 클립 추출
            boolean success = executeFFmpeg(rtspUrl, outputPath.toString(), durationSeconds);
            if (!success) {
                throw new RuntimeException("FFmpeg 클립 추출 실패");
            }

            // MinIO에 업로드
            byte[] clipData = Files.readAllBytes(outputPath);
            String clipKey = s3Service.uploadEventClip(eventId, clipData, "video/mp4");

            log.info("클립 저장 완료: camera={}, event={}, size={}KB",
                    camera.getName(), eventId, clipData.length / 1024);

            return clipKey;

        } catch (Exception e) {
            log.error("클립 추출/저장 실패: camera={}, event={}", camera.getName(), eventId, e);
            throw new RuntimeException("클립 추출 실패", e);
        } finally {
            // 임시 파일 삭제
            try {
                Files.deleteIfExists(outputPath);
            } catch (Exception e) {
                log.warn("임시 파일 삭제 실패: {}", outputPath);
            }
        }
    }

    /**
     * 기본 시간으로 클립 추출
     */
    public String extractAndSaveClip(UUID cameraId, UUID eventId) {
        return extractAndSaveClip(cameraId, eventId, defaultClipDuration);
    }

    /**
     * FFmpeg 명령 실행
     */
    private boolean executeFFmpeg(String rtspUrl, String outputPath, int durationSeconds) {
        try {
            // FFmpeg 명령어 구성
            // -rtsp_transport tcp: TCP 사용 (안정성)
            // -t: 녹화 시간
            // -c copy: 코덱 복사 (빠름)
            // -y: 파일 덮어쓰기
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-rtsp_transport", "tcp",
                    "-i", rtspUrl,
                    "-t", String.valueOf(durationSeconds),
                    "-c", "copy",
                    "-y",
                    outputPath
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 로그 출력 (디버깅용)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            // 타임아웃 설정 (클립 길이 + 여유 시간)
            boolean finished = process.waitFor(durationSeconds + 30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg 타임아웃: {}", rtspUrl);
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg 종료 코드: {}", exitCode);
                return false;
            }

            // 파일 생성 확인
            File outputFile = new File(outputPath);
            if (!outputFile.exists() || outputFile.length() == 0) {
                log.error("클립 파일 생성 실패: {}", outputPath);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("FFmpeg 실행 실패: {}", e.getMessage());
            return false;
        }
    }
}
