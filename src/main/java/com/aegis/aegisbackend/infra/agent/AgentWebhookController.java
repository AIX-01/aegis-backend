package com.aegis.aegisbackend.infra.agent;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.agent.dto.AnalysisResultRequest;
import com.aegis.aegisbackend.infra.agent.dto.CreateEventRequest;
import com.aegis.aegisbackend.infra.mediamtx.ClipExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;


/**
 * Agent 컨트롤러 (내부망 전용)
 * - 분석 대상 카메라 조회: GET /internal/agent/cameras/analysis
 * - 이벤트 생성: POST /internal/agent/events (클립 자동 추출 포함)
 * - 분석 결과 추가: PATCH /internal/agent/events/{id}/analysis
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class AgentWebhookController {

    private final ClipExtractionService clipExtractionService;
    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    /**
     * 클립 추출 테스트 엔드포인트
     * - 실제 이벤트 생성 없이 클립 추출만 테스트
     */
    @GetMapping("/test/clip/{cameraName}")
    public ResponseEntity<?> testClipExtraction(@PathVariable String cameraName) {
        log.info("클립 추출 테스트: cameraName={}", cameraName);

        try {
            UUID testId = UUID.randomUUID();
            String clipUrl = clipExtractionService.extractAndSaveClip(cameraName, testId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "clipUrl", clipUrl,
                    "testEventId", testId.toString()
            ));
        } catch (Exception e) {
            log.error("클립 추출 테스트 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage(),
                            "errorClass", e.getClass().getSimpleName()
                    ));
        }
    }

    /**
     * 분석 대상 카메라 목록 조회
     * - Python Agent가 Redis Pub/Sub 수신 후 호출
     * - enabled=true && analysisEnabled=true인 카메라만 반환
     */
    @GetMapping("/cameras/analysis")
    public ResponseEntity<?> getAnalysisCameras() {
        log.debug("분석 대상 카메라 목록 조회");

        var cameras = cameraRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()) && Boolean.TRUE.equals(c.getAnalysisEnabled()))
                .map(c -> Map.of(
                        "id", c.getId().toString(),
                        "name", c.getName(),
                        "enabled", c.getEnabled(),
                        "analysisEnabled", c.getAnalysisEnabled()
                ))
                .toList();

        return ResponseEntity.ok(Map.of("cameras", cameras));
    }

    /**
     * 이벤트 생성 (클립 자동 추출 포함)
     * - 이벤트 = 메타데이터 + 클립 영상이 함께 포함된 단일 객체
     * - cameraName으로 카메라 조회 → 이벤트 생성 → 클립 추출 → DB 저장
     * - 알림 생성 + SSE 브로드캐스트
     * - 응답으로 전체 EventDto 반환
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody CreateEventRequest request) {
        log.info("이벤트 생성 요청: cameraName={}, eventType={}", request.getCameraName(), request.getEventType());

        try {
            // cameraName(실명)으로 카메라 조회
            Camera camera = cameraRepository.findByName(request.getCameraName())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

            // timestamp 파싱 (없으면 현재 시간)
            LocalDateTime timestamp = request.getTimestamp() != null
                    ? LocalDateTime.parse(request.getTimestamp())
                    : LocalDateTime.now();

            // 이벤트 생성 (클립 URL은 추출 후 설정)
            Event event = Event.builder()
                    .camera(camera)
                    .type(EventType.fromValue(request.getEventType()))
                    .timestamp(timestamp)
                    .status(EventStatus.PROCESSING)
                    .description(generateDescription(request.getEventType(), camera.getLocation()))
                    .build();

            Event savedEvent = eventRepository.save(event);
            log.info("이벤트 생성 완료: eventId={}", savedEvent.getId());

            // 클립 추출 및 저장 (동기)
            try {
                String clipUrl = clipExtractionService.extractAndSaveClip(
                        camera.getName(),
                        savedEvent.getId()
                );
                savedEvent.setClipUrl(clipUrl);
                eventRepository.save(savedEvent);
                log.info("클립 추출 완료: eventId={}, clipUrl={}", savedEvent.getId(), clipUrl);
            } catch (Exception e) {
                log.warn("클립 추출 실패, 이벤트는 유지됨: eventId={}, error={}", savedEvent.getId(), e.getMessage());
            }

            // 알림 생성
            notificationService.createNotificationsForEvent(savedEvent);

            // SSE 브로드캐스트
            EventDto eventDto = EventDto.from(savedEvent);
            sseEmitterService.broadcastEvent(eventDto);

            return ResponseEntity.status(HttpStatus.CREATED).body(eventDto);

        } catch (BusinessException e) {
            log.error("이벤트 생성 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("이벤트 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Agent 분석 결과 추가
     * - 이벤트에 분석 결과 업데이트
     * - 상태를 RESOLVED로 변경
     * - 응답으로 전체 EventDto 반환
     */
    @PatchMapping("/events/{eventId}/analysis")
    public ResponseEntity<?> addAnalysisResult(
            @PathVariable UUID eventId,
            @RequestBody AnalysisResultRequest request) {
        log.info("분석 결과 추가 요청: eventId={}", eventId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            // 분석 결과 업데이트
            event.setAgentAction(request.getAgentAction());
            event.setSummary(request.getSummary());
            event.setAnalysisReport(request.getAnalysisReport());
            event.setStatus(EventStatus.RESOLVED);

            eventRepository.save(event);
            log.info("분석 결과 추가 완료: eventId={}", eventId);

            // SSE 브로드캐스트
            EventDto eventDto = EventDto.from(event);
            sseEmitterService.broadcastEvent(eventDto);

            return ResponseEntity.ok(eventDto);

        } catch (BusinessException e) {
            log.error("분석 결과 추가 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("분석 결과 추가 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 이벤트 타입에 따른 기본 설명 생성
     */
    private String generateDescription(String eventType, String cameraLocation) {
        String typeKorean = switch (eventType.toLowerCase()) {
            case "assault" -> "폭행";
            case "burglary" -> "절도";
            case "dump" -> "투기";
            case "swoon" -> "실신";
            case "vandalism" -> "파손";
            default -> "이상상황";
        };
        return cameraLocation + "에서 " + typeKorean + " 감지";
    }
}
