package com.aegis.aegisbackend.infra.agent;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.entity.EventAction;
import com.aegis.aegisbackend.domain.event.repository.EventActionRepository;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.agent.dto.CreateEventRequest;
import com.aegis.aegisbackend.infra.agent.dto.EventActionRequest;
import com.aegis.aegisbackend.infra.agent.dto.EventActionUpdateRequest;
import com.aegis.aegisbackend.infra.agent.dto.EventUpdateRequest;
import com.aegis.aegisbackend.infra.agent.dto.PendingActionResponse;
import com.aegis.aegisbackend.infra.agent.service.PendingActionService;
import com.aegis.aegisbackend.infra.s3.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 컨트롤러 (내부망 전용)
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class AgentWebhookController {

    private final CameraRepository cameraRepository;
    private final EventRepository eventRepository;
    private final EventActionRepository eventActionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final PendingActionService pendingActionService;
    private final S3Service s3Service;

    /**
     * 이벤트 생성
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody @Valid CreateEventRequest request) {
        log.info("이벤트 생성 요청: cameraId={}, risk={}, type={}",
                request.getCameraId(), request.getRisk(), request.getType());

        try {
            Camera camera = cameraRepository.findById(UUID.fromString(request.getCameraId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

            LocalDateTime occurredAt = request.getOccurredAt() != null
                    ? LocalDateTime.parse(request.getOccurredAt())
                    : LocalDateTime.now();

            Event event = Event.builder()
                    .camera(camera)
                    .risk(EventRisk.fromValue(request.getRisk()))
                    .type(EventType.fromValue(request.getType()))
                    .occurredAt(occurredAt)
                    .status(EventStatus.PROCESSING)
                    .build();

            Event savedEvent = eventRepository.save(event);
            log.info("이벤트 생성 완료: eventId={}", savedEvent.getId());

            notificationService.createEventNotifications(savedEvent);
            sseEmitterService.broadcastEvent(EventDto.from(savedEvent));

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("eventId", savedEvent.getId().toString()));

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
     * 이벤트 수정
     */
    @PatchMapping("/events/{eventId}")
    public ResponseEntity<?> updateEvent(
            @PathVariable UUID eventId,
            @RequestBody EventUpdateRequest request) {
        log.info("이벤트 수정 요청: eventId={}", eventId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            if (request.getRisk() != null) {
                event.setRisk(EventRisk.fromValue(request.getRisk()));
            }
            if (request.getType() != null) {
                event.setType(EventType.fromValue(request.getType()));
            }
            if (request.getSummary() != null) {
                event.setSummary(request.getSummary());
            }
            if (request.getReport() != null) {
                event.setReport(request.getReport());
            }
            if (request.getStatus() != null) {
                event.setStatus(EventStatus.fromValue(request.getStatus()));
            }

            eventRepository.save(event);
            log.info("이벤트 수정 완료: eventId={}", eventId);

            notificationService.createEventUpdateNotifications(event);
            sseEmitterService.broadcastEvent(EventDto.from(event));

            return ResponseEntity.ok(Map.of("eventId", eventId.toString()));

        } catch (BusinessException e) {
            log.error("이벤트 수정 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("이벤트 수정 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 클립 업로드용 presigned URL 발급
     */
    @GetMapping("/events/{eventId}/clip/upload-url")
    public ResponseEntity<?> getClipUploadUrl(@PathVariable UUID eventId) {
        log.info("클립 업로드 URL 요청: eventId={}", eventId);

        try {
            eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            String uploadUrl = s3Service.generateUploadUrl(eventId);
            return ResponseEntity.ok(Map.of("uploadUrl", uploadUrl));

        } catch (BusinessException e) {
            log.error("업로드 URL 생성 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 클립 업로드 완료 확인
     */
    @PostMapping("/events/{eventId}/clip/confirm")
    public ResponseEntity<?> confirmClip(@PathVariable UUID eventId) {
        log.info("클립 업로드 완료 확인: eventId={}", eventId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            if (!s3Service.clipExists(eventId)) {
                log.warn("클립을 찾을 수 없음: eventId={}", eventId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "클립을 찾을 수 없습니다"));
            }

            String clipUrl = "clips/" + eventId + ".mp4";
            event.setClipUrl(clipUrl);
            eventRepository.save(event);

            log.info("클립 확정 완료: eventId={}, clipUrl={}", eventId, clipUrl);
            sseEmitterService.broadcastEvent(EventDto.from(event));

            return ResponseEntity.ok(Map.of("clipUrl", clipUrl));

        } catch (BusinessException e) {
            log.error("클립 확정 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("클립 확정 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 이벤트 액션 생성
     */
    @PostMapping("/events/{eventId}/actions")
    public ResponseEntity<?> createEventAction(
            @PathVariable UUID eventId,
            @RequestBody @Valid EventActionRequest request) {
        log.info("이벤트 액션 생성 요청: eventId={}, action={}", eventId, request.getAction());

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            EventAction eventAction = EventAction.builder()
                    .event(event)
                    .action(request.getAction())
                    .description(request.getDescription())
                    .build();

            EventAction savedAction = eventActionRepository.save(eventAction);
            log.info("이벤트 액션 생성 완료: actionId={}", savedAction.getId());

            // 알림 생성 및 SSE 전송
            notificationService.createActionNotifications(event, request.getAction(), request.getDescription());
            sseEmitterService.broadcastActionUpdate(eventId, savedAction.getId());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("actionId", savedAction.getId().toString()));

        } catch (BusinessException e) {
            log.error("이벤트 액션 생성 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("이벤트 액션 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 이벤트 액션 수정
     */
    @PatchMapping("/events/{eventId}/actions/{actionId}")
    public ResponseEntity<?> updateEventAction(
            @PathVariable UUID eventId,
            @PathVariable UUID actionId,
            @RequestBody @Valid EventActionUpdateRequest request) {
        log.info("이벤트 액션 수정 요청: eventId={}, actionId={}", eventId, actionId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            EventAction eventAction = eventActionRepository.findById(actionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_ACTION_NOT_FOUND));

            if (request.getUserId() != null && !request.getUserId().isEmpty()) {
                User user = userRepository.findById(UUID.fromString(request.getUserId()))
                        .orElse(null);
                eventAction.setUser(user);
            }
            eventAction.setAction(request.getAction());
            eventAction.setDescription(request.getDescription());

            eventActionRepository.save(eventAction);
            log.info("이벤트 액션 수정 완료: actionId={}", actionId);

            // 알림 생성 및 SSE 전송
            notificationService.createActionUpdateNotifications(event, request.getAction(), request.getDescription());
            sseEmitterService.broadcastActionUpdate(eventId, actionId);

            return ResponseEntity.ok(Map.of("actionId", actionId.toString()));

        } catch (BusinessException e) {
            log.error("이벤트 액션 수정 실패: {}", e.getMessage());
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("이벤트 액션 수정 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 이벤트 액션 승인 대기 (DeferredResult로 사용자 응답까지 홀딩)
     */
    @PostMapping("/events/{eventId}/actions/{actionId}/pending")
    public DeferredResult<PendingActionResponse> pendingAction(
            @PathVariable UUID eventId,
            @PathVariable UUID actionId) {
        log.info("Pending 액션 요청: eventId={}, actionId={}", eventId, actionId);

        // 이벤트 존재 확인
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // 액션 존재 확인
        EventAction eventAction = eventActionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_ACTION_NOT_FOUND));

        // DeferredResult 등록
        DeferredResult<PendingActionResponse> deferredResult =
                pendingActionService.registerPending(actionId, eventId);

        // 알림 생성 및 SSE 전송
        notificationService.createPendingActionNotifications(
                event, eventAction.getAction(), eventAction.getDescription());

        // SSE로 프론트엔드에 pending 상태 알림
        sseEmitterService.broadcastActionPending(eventId, actionId,
                eventAction.getAction(), eventAction.getDescription());

        return deferredResult;
    }
}
