package com.aegis.aegisbackend.domain.notification.service;

import com.aegis.aegisbackend.domain.notification.dto.NotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 관리 서비스
 * - 사용자별 SSE 연결 관리
 * - 실시간 알림 푸시
 */
@Slf4j
@Service
public class SseEmitterService {

    // 사용자별 SSE Emitter 저장 (userId -> SseEmitter)
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SSE 연결 타임아웃 (30분)
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /**
     * SSE 연결 생성
     */
    public SseEmitter createEmitter(UUID userId) {
        // 기존 연결이 있으면 제거
        removeEmitter(userId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(userId, emitter);

        // 연결 종료 시 정리
        emitter.onCompletion(() -> {
            log.debug("SSE 연결 완료: userId={}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE 연결 타임아웃: userId={}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.warn("SSE 연결 오류: userId={}, error={}", userId, e.getMessage());
            emitters.remove(userId);
        });

        // 연결 확인 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 성공"));
            log.info("SSE 연결 생성: userId={}", userId);
        } catch (IOException e) {
            log.error("SSE 연결 확인 전송 실패: userId={}", userId);
            emitters.remove(userId);
        }

        return emitter;
    }

    /**
     * SSE 연결 제거
     */
    public void removeEmitter(UUID userId) {
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            emitter.complete();
            log.debug("SSE 연결 제거: userId={}", userId);
        }
    }

    /**
     * 특정 사용자에게 알림 전송
     */
    public void sendNotification(UUID userId, NotificationDto notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("SSE 연결 없음: userId={}", userId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(notification));
            log.debug("SSE 알림 전송: userId={}, notificationId={}", userId, notification.getId());
        } catch (IOException e) {
            log.warn("SSE 알림 전송 실패: userId={}", userId);
            emitters.remove(userId);
        }
    }

    /**
     * 여러 사용자에게 알림 전송
     */
    public void sendNotificationToUsers(Iterable<UUID> userIds, NotificationDto notification) {
        for (UUID userId : userIds) {
            sendNotification(userId, notification);
        }
    }

    /**
     * 모든 사용자에게 알림 전송 (브로드캐스트)
     */
    public void broadcastNotification(NotificationDto notification) {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.warn("SSE 브로드캐스트 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 카메라 이벤트 브로드캐스트 (추가/삭제/상태변경)
     */
    public void broadcastCamera(Object cameraData) {
        log.debug("카메라 이벤트 브로드캐스트: 연결된 사용자 수={}", emitters.size());
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("camera")
                        .data(cameraData));
            } catch (IOException e) {
                log.warn("카메라 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 이벤트 브로드캐스트 (이벤트 생성/삭제/상태변경)
     */
    public void broadcastEvent(Object eventData) {
        log.debug("이벤트 브로드캐스트: 연결된 사용자 수={}", emitters.size());
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("event")
                        .data(eventData));
            } catch (IOException e) {
                log.warn("이벤트 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 이벤트 삭제 브로드캐스트
     */
    public void broadcastEventDeleted(String eventId) {
        log.debug("이벤트 삭제 브로드캐스트: eventId={}, 연결된 사용자 수={}", eventId, emitters.size());
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("event-deleted")
                        .data(Map.of("id", eventId)));
            } catch (IOException e) {
                log.warn("이벤트 삭제 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 멤버 이벤트 브로드캐스트 (승인/삭제/역할변경)
     */
    public void broadcastMember(Object memberData) {
        log.debug("멤버 이벤트 브로드캐스트: 연결된 사용자 수={}", emitters.size());
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("member")
                        .data(memberData));
            } catch (IOException e) {
                log.warn("멤버 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 액션 업데이트 브로드캐스트 (생성/수정 - 토스트 없이 모달만 업데이트)
     */
    public void broadcastActionUpdate(UUID eventId, UUID actionId) {
        log.info("액션 업데이트 브로드캐스트: eventId={}, actionId={}", eventId, actionId);
        Map<String, Object> data = Map.of(
                "eventId", eventId.toString(),
                "actionId", actionId.toString()
        );
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("action-update")
                        .data(data));
            } catch (IOException e) {
                log.warn("액션 업데이트 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 액션 승인 대기 브로드캐스트
     */
    public void broadcastActionPending(UUID eventId, UUID actionId, String action, String description) {
        log.info("액션 승인 대기 브로드캐스트: eventId={}, actionId={}, 연결된 사용자 수={}",
                eventId, actionId, emitters.size());
        Map<String, Object> data = Map.of(
                "eventId", eventId.toString(),
                "actionId", actionId.toString(),
                "action", action,
                "description", description
        );
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("action-pending")
                        .data(data));
            } catch (IOException e) {
                log.warn("액션 승인 대기 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 액션 해결됨 브로드캐스트 (승인/거부 완료)
     */
    public void broadcastActionResolved(UUID eventId, UUID actionId) {
        log.info("액션 해결됨 브로드캐스트: eventId={}, actionId={}", eventId, actionId);
        Map<String, Object> data = Map.of(
                "eventId", eventId.toString(),
                "actionId", actionId.toString()
        );
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("action-resolved")
                        .data(data));
            } catch (IOException e) {
                log.warn("액션 해결됨 SSE 전송 실패: userId={}", userId);
                emitters.remove(userId);
            }
        });
    }

    /**
     * 현재 연결된 사용자 수
     */
    public int getConnectedUserCount() {
        return emitters.size();
    }
}
