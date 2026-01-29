package com.aegis.aegisbackend.infra.agent;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
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
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }


    /**
     * 이벤트 생성 (비동기 클립 추출)
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestBody CreateEventRequest request) {
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

            // 비동기 클립 추출
            clipExtractionService.extractAndSaveClipAsync(camera.getName(), savedEvent.getId());

            // 알림 생성 (ALERT)
            notificationService.createEventNotifications(savedEvent);

            // SSE 브로드캐스트
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
     * 분석 결과 추가
     */
    @PatchMapping("/events/{eventId}/analysis")
    public ResponseEntity<?> addAnalysisResult(
            @PathVariable UUID eventId,
            @RequestBody AnalysisResultRequest request) {
        log.info("분석 결과 추가 요청: eventId={}", eventId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

            event.setSummary(request.getSummary());
            event.setRiskScore(request.getRiskScore());
            event.setActions(request.getActions());
            event.setRagReferences(request.getRagReferences());
            event.setReport(request.getReport());
            event.setStatus(EventStatus.ANALYZED);

            eventRepository.save(event);
            log.info("분석 결과 추가 완료: eventId={}", eventId);

            // 알림 생성 (WARNING)
            notificationService.createAnalysisNotifications(event);

            // SSE 브로드캐스트
            sseEmitterService.broadcastEvent(EventDto.from(event));

            return ResponseEntity.ok(Map.of("eventId", eventId.toString()));

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
}
