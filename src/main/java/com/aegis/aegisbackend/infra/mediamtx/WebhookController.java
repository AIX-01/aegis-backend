package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.stream.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.domain.stream.service.FrameBufferService;
import com.aegis.aegisbackend.domain.stream.service.StreamService;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MediaMTX Webhook 컨트롤러 (내부망 전용)
 * - 카메라 추가/삭제 알림 수신 → 동기화 트리거
 * - 스트림 인증 검증
 * - 프레임 수신
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhooks/mediamtx")
@RequiredArgsConstructor
public class WebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;
    private final StreamService streamService;
    private final FrameBufferService frameBufferService;
    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final ClipExtractionService clipExtractionService;
    private final NotificationService notificationService;

    // 카메라 이름 → Camera 로컬 캐시 (초당 프레임 처리 시 DB 조회 최소화)
    private final Map<String, Camera> cameraCache = new ConcurrentHashMap<>();

    /**
     * 카메라 동기화 트리거 (단일 엔드포인트)
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Boolean>> handleSyncTrigger(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("MediaMTX 동기화 트리거: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 스트림 인증 검증 */
    @PostMapping("/auth")
    public ResponseEntity<?> validateAuth(@RequestBody MediaMTXAuthRequest request) {
        boolean valid = streamService.validateStreamAuth(
                request.getUser(), request.getPath(), request.getAction());
        return valid ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** 프레임 수신 (썸네일 + AI 버퍼) */
    @PostMapping(value = "/frame/{cameraName}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> receiveFrame(
            @PathVariable String cameraName,
            @RequestBody byte[] frameData) {

        // 캐시 우선 조회, 미스 시 DB 조회
        Camera camera = cameraCache.computeIfAbsent(cameraName, name ->
            cameraRepository.findByName(name).orElse(null)
        );

        if (camera == null) {
            return ResponseEntity.notFound().build();
        }

        if (!camera.getActive()) {
            return ResponseEntity.ok(Map.of("processed", false, "reason", "inactive"));
        }

        frameBufferService.processFrame(camera.getId(), frameData);

        return ResponseEntity.ok(Map.of("processed", true));
    }

    /** 전체 캐시 무효화 */
    public void invalidateCameraCache() {
        cameraCache.clear();
        log.debug("카메라 캐시 무효화");
    }

    /** 특정 카메라 캐시 무효화 */
    public void invalidateCameraCache(String cameraName) {
        cameraCache.remove(cameraName);
        log.debug("카메라 캐시 무효화: {}", cameraName);
    }

    /**
     * 클립 추출 웹훅 (AI 백엔드에서 호출)
     * - 이벤트 생성 + HLS 세그먼트 → MP4 → MinIO 저장
     *
     * @param request cameraId, eventType, description 등
     */
    @PostMapping("/clip/extract")
    public ResponseEntity<?> extractClip(@RequestBody ClipExtractRequest request) {
        log.info("클립 추출 요청: cameraId={}, eventType={}", request.getCameraId(), request.getEventType());

        try {
            // 1. 카메라 확인
            Camera camera = cameraRepository.findById(request.getCameraId())
                    .orElse(null);
            if (camera == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "카메라를 찾을 수 없습니다"));
            }

            // 2. 이벤트 생성
            Event event = Event.builder()
                    .camera(camera)
                    .type(EventType.fromValue(request.getEventType()))
                    .timestamp(LocalDateTime.now())
                    .status(EventStatus.PROCESSING)
                    .description(request.getDescription())
                    .aiAction(request.getRecommendedAction())
                    .summary(request.getSummary())
                    .analysisReport(request.getAnalysisReport())
                    .build();

            Event savedEvent = eventRepository.save(event);
            log.info("이벤트 생성: eventId={}", savedEvent.getId());

            // 3. 알림 생성 (카메라 접근 권한이 있는 사용자들에게)
            notificationService.createNotificationsForEvent(savedEvent);
            log.info("알림 생성 완료: eventId={}", savedEvent.getId());

            // 4. 클립 추출 (비동기) - HLS 세그먼트 → MP4 → MinIO
            clipExtractionService.extractAndSaveClipAsync(camera.getId(), savedEvent.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "eventId", savedEvent.getId().toString()
            ));

        } catch (Exception e) {
            log.error("클립 추출 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @lombok.Data
    public static class ClipExtractRequest {
        private UUID cameraId;
        private String eventType;
        private String description;
        private String recommendedAction;
        private String summary;
        private String analysisReport;
    }
}
