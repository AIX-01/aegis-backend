package com.aegis.aegisbackend.domain.notification.controller;

import com.aegis.aegisbackend.domain.event.entity.EventAction;
import com.aegis.aegisbackend.domain.event.repository.EventActionRepository;
import com.aegis.aegisbackend.domain.notification.dto.NotificationDto;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.infra.agent.service.PendingActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 알림 API
 * - 사용자별 알림 조회 및 삭제
 * - SSE를 통한 실시간 알림 푸시
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final PendingActionService pendingActionService;
    private final EventActionRepository eventActionRepository;

    /**
     * SSE 연결 (실시간 알림 수신)
     * - 클라이언트는 EventSource로 연결
     * - 새 알림 발생 시 서버에서 푸시
     * - 연결 시 현재 pending 액션 목록 전송
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        SseEmitter emitter = sseEmitterService.createEmitter(userId);

        // 현재 pending 액션 목록 전송
        sendPendingActions(emitter);

        return emitter;
    }

    /**
     * 현재 pending 액션 목록 전송
     */
    private void sendPendingActions(SseEmitter emitter) {
        Set<UUID> pendingActionIds = pendingActionService.getPendingActionIds();
        if (pendingActionIds.isEmpty()) {
            return;
        }

        for (UUID actionId : pendingActionIds) {
            try {
                EventAction action = eventActionRepository.findById(actionId).orElse(null);
                if (action != null) {
                    Map<String, Object> data = Map.of(
                            "eventId", action.getEvent().getId().toString(),
                            "actionId", actionId.toString(),
                            "action", action.getAction(),
                            "description", action.getDescription()
                    );
                    emitter.send(SseEmitter.event()
                            .name("action-pending")
                            .data(data));
                    log.debug("Pending 액션 전송: actionId={}", actionId);
                }
            } catch (IOException e) {
                log.warn("Pending 액션 전송 실패: actionId={}", actionId);
            }
        }
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getAllNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<NotificationDto> notifications = notificationService.getNotificationsByUserId(userId);
        return ResponseEntity.ok(notifications);
    }


    @DeleteMapping
    public ResponseEntity<Map<String, Boolean>> deleteAllNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        notificationService.deleteAllNotifications(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
