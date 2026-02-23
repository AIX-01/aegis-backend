package com.aegis.aegisbackend.domain.notification.service;

import com.aegis.aegisbackend.domain.notification.dto.NotificationDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.notification.entity.Notification;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.global.common.enums.NotificationType;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.repository.NotificationRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsByUserId(UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return notifications.stream()
                .map(this::toNotificationDto)
                .toList();
    }


    @Transactional
    public void createNotification(UUID userId, UUID eventId, NotificationType type, String title, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Event event = null;
        if (eventId != null) {
            event = eventRepository.findById(eventId).orElse(null);
        }

        Notification notification = Notification.builder()
                .user(user)
                .event(event)
                .type(type)
                .title(title)
                .message(message)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created: userId={}, type={}, title={}", userId, type, title);

        // SSE로 실시간 알림 전송
        NotificationDto dto = toNotificationDto(saved);
        sseEmitterService.sendNotification(userId, dto);
    }

    /**
     * 모든 알림 삭제
     */
    @Transactional
    public void deleteAllNotifications(UUID userId) {
        notificationRepository.deleteAllByUserId(userId);
        log.info("모든 알림 삭제 완료: userId={}", userId);
    }

    /**
     * 이벤트에 연결된 모든 알림 삭제
     */
    @Transactional
    public void deleteNotificationsByEventId(UUID eventId) {
        notificationRepository.deleteByEventId(eventId);
        log.info("이벤트 관련 알림 삭제 완료: eventId={}", eventId);
    }

    /**
     * risk 기반 NotificationType 결정
     * - abnormal: ALERT (빨강)
     * - suspicious: WARNING (노랑)
     * - normal: INFO (기본)
     */
    private NotificationType getNotificationTypeByRisk(EventRisk risk) {
        return switch (risk) {
            case ABNORMAL -> NotificationType.ALERT;
            case SUSPICIOUS -> NotificationType.WARNING;
            case NORMAL -> NotificationType.INFO;
        };
    }

    /**
     * 이벤트 생성 시 알림 (POST /events)
     */
    @Transactional
    public void createEventNotifications(Event event) {
        Camera camera = event.getCamera();
        List<User> users = getNotificationTargetUsers(camera.getId());
        NotificationType type = getNotificationTypeByRisk(event.getRisk());

        String title = getEventTitle(event.getType());
        String message = String.format("[%s] %s 감지", camera.getLocation(), getEventTypeKorean(event.getType()));

        for (User user : users) {
            createNotification(user.getId(), event.getId(), type, title, message);
        }
        log.info("이벤트 알림 생성 완료: eventId={}, risk={}, users={}", event.getId(), event.getRisk(), users.size());
    }

    /**
     * 이벤트 수정 시 알림 (PATCH /events)
     */
    @Transactional
    public void createEventUpdateNotifications(Event event) {
        Camera camera = event.getCamera();
        List<User> users = getNotificationTargetUsers(camera.getId());
        NotificationType type = getNotificationTypeByRisk(event.getRisk());

        String title = "분석 완료";
        String message = String.format("[%s] 상세 분석이 완료되었습니다.", camera.getLocation());

        for (User user : users) {
            createNotification(user.getId(), event.getId(), type, title, message);
        }
        log.info("이벤트 수정 알림 생성: eventId={}, risk={}, users={}", event.getId(), event.getRisk(), users.size());
    }

    /**
     * 액션 생성 시 알림 (POST /actions)
     */
    @Transactional
    public void createActionNotifications(Event event, String action, String description) {
        Camera camera = event.getCamera();
        List<User> users = getNotificationTargetUsers(camera.getId());
        NotificationType type = getNotificationTypeByRisk(event.getRisk());

        String title = "액션 생성";
        String message = String.format("[%s] %s", action, description);

        for (User user : users) {
            createNotification(user.getId(), event.getId(), type, title, message);
        }
        log.info("액션 생성 알림: eventId={}, action={}, users={}", event.getId(), action, users.size());
    }

    /**
     * 액션 수정 시 알림 (PATCH /actions)
     */
    @Transactional
    public void createActionUpdateNotifications(Event event, String action, String description) {
        Camera camera = event.getCamera();
        List<User> users = getNotificationTargetUsers(camera.getId());
        NotificationType type = getNotificationTypeByRisk(event.getRisk());

        String title = "액션 수정";
        String message = String.format("[%s] %s", action, description);

        for (User user : users) {
            createNotification(user.getId(), event.getId(), type, title, message);
        }
        log.info("액션 수정 알림: eventId={}, action={}, users={}", event.getId(), action, users.size());
    }

    /**
     * 액션 승인 요청 시 알림 (POST /pending)
     */
    @Transactional
    public void createPendingActionNotifications(Event event, String action, String description) {
        Camera camera = event.getCamera();
        List<User> users = getNotificationTargetUsers(camera.getId());
        NotificationType type = getNotificationTypeByRisk(event.getRisk());

        String title = "승인 요청";
        String message = String.format("[%s] %s", action, description);

        for (User user : users) {
            createNotification(user.getId(), event.getId(), type, title, message);
        }
        log.info("승인 요청 알림 생성: eventId={}, action={}, users={}", event.getId(), action, users.size());
    }

    private List<User> getNotificationTargetUsers(UUID cameraId) {
        List<User> assignedUsers = userRepository.findUsersByCameraId(cameraId);
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);
        assignedUsers.addAll(admins);
        return assignedUsers.stream().distinct().toList();
    }

    private String getEventTitle(EventType type) {
        return switch (type) {
            case ASSAULT -> "폭행 감지";
            case BURGLARY -> "절도 감지";
            case DUMP -> "투기 감지";
            case SWOON -> "실신 감지";
            case VANDALISM -> "파손 감지";
        };
    }

    private String getEventTypeKorean(EventType type) {
        return switch (type) {
            case ASSAULT -> "폭행";
            case BURGLARY -> "절도";
            case DUMP -> "투기";
            case SWOON -> "실신";
            case VANDALISM -> "파손";
        };
    }

    private NotificationDto toNotificationDto(Notification notification) {
        LocalDateTime timestamp = notification.getCreatedAt() != null
                ? notification.getCreatedAt()
                : LocalDateTime.now();

        return NotificationDto.builder()
                .id(notification.getId().toString())
                .type(notification.getType().getValue())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .timestamp(timestamp.toString())
                .eventId(notification.getEvent() != null ? notification.getEvent().getId().toString() : null)
                .build();
    }
}
