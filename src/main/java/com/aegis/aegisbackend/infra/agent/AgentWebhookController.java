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
import com.aegis.aegisbackend.infra.agent.dto.ClipRequest;
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
 * - 클립 추출: POST /internal/agent/clips
 * - 이벤트 생성: POST /internal/agent/events
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
     * 클립 추출 (이벤트 없이)
     * - HLS 세그먼트 → MP4 → MinIO 저장
     * - clipKey 반환 (이벤트 생성 시 사용)
     */
    @PostMapping("/clips")
    public ResponseEntity<?> extractClip(@RequestBody ClipRequest request) {
        log.info("클립 추출 요청: cameraId={}", request.getCameraId());

        try {
            // 카메라 존재 확인
            if (!cameraRepository.existsById(request.getCameraId())) {
                throw new BusinessException(ErrorCode.CAMERA_NOT_FOUND);
            }

            int segmentCount = request.getSegmentCount() != null ? request.getSegmentCount() : 10;

            // 클립 추출 (동기)
            String clipKey = clipExtractionService.extractClipOnly(request.getCameraId(), segmentCount);

            log.info("클립 추출 완료: cameraId={}, clipKey={}", request.getCameraId(), clipKey);

            return ResponseEntity.ok(Map.of(
                    "clipKey", clipKey,
                    "cameraId", request.getCameraId().toString()
            ));

        } catch (BusinessException e) {
            log.error("클립 추출 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("클립 추출 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 이벤트 생성 (클립 포함)
     * - 이벤트 DB 저장
     * - 알림 생성 + SSE 브로드캐스트
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody CreateEventRequest request) {
        log.info("이벤트 생성 요청: cameraId={}, eventType={}", request.getCameraId(), request.getEventType());

        try {
            // 카메라 확인
            Camera camera = cameraRepository.findById(request.getCameraId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

            // timestamp 파싱 (없으면 현재 시간)
            LocalDateTime timestamp = request.getTimestamp() != null
                    ? LocalDateTime.parse(request.getTimestamp())
                    : LocalDateTime.now();

            // 이벤트 생성
            Event event = Event.builder()
                    .camera(camera)
                    .type(EventType.fromValue(request.getEventType()))
                    .timestamp(timestamp)
                    .status(EventStatus.PROCESSING)
                    .description(request.getDescription())
                    .clipUrl(request.getClipKey())
                    .build();

            Event savedEvent = eventRepository.save(event);
            log.info("이벤트 생성 완료: eventId={}", savedEvent.getId());

            // 알림 생성
            notificationService.createNotificationsForEvent(savedEvent);

            // SSE 브로드캐스트
            EventDto eventDto = toEventDto(savedEvent);
            sseEmitterService.broadcastEvent(eventDto);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "eventId", savedEvent.getId().toString(),
                    "status", savedEvent.getStatus().getValue()
            ));

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
            EventDto eventDto = toEventDto(event);
            sseEmitterService.broadcastEvent(eventDto);

            return ResponseEntity.ok(Map.of(
                    "eventId", eventId.toString(),
                    "status", event.getStatus().getValue()
            ));

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

    private EventDto toEventDto(Event event) {
        return EventDto.builder()
                .id(event.getId().toString())
                .cameraId(event.getCamera().getId().toString())
                .cameraName(event.getCamera().getAlias())
                .type(event.getType().getValue())
                .timestamp(event.getTimestamp().toString())
                .status(event.getStatus().getValue())
                .description(event.getDescription())
                .agentAction(event.getAgentAction())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .analysisReport(event.getAnalysisReport())
                .build();
    }
}
