package com.aegis.aegisbackend.domain.notification.service;

import com.aegis.aegisbackend.domain.notification.dto.NotificationDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.notification.entity.Notification;
import com.aegis.aegisbackend.domain.user.entity.User;
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

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
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
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.debug("Notification created for user: {}", userId);

        // SSE로 실시간 알림 전송
        NotificationDto dto = toNotificationDto(saved);
        sseEmitterService.sendNotification(userId, dto);
    }

    @Transactional
    public NotificationDto markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        notification.setRead(true);
        notificationRepository.save(notification);

        return toNotificationDto(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        int updated = notificationRepository.markAllAsReadByUserId(userId);
        log.info("Marked {} notifications as read for user: {}", updated, userId);
    }

    @Transactional
    public void deleteNotification(UUID notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notificationRepository.deleteById(notificationId);
    }

    /** 이벤트 발생 시 관련 사용자들에게 알림 생성 */
    @Transactional
    public void createNotificationsForEvent(Event event) {
        Camera camera = event.getCamera();

        // 해당 카메라에 할당된 사용자들 + Admin 조회
        List<User> assignedUsers = userRepository.findUsersByCameraId(camera.getId());
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);
        assignedUsers.addAll(admins);
        List<User> uniqueUsers = assignedUsers.stream().distinct().toList();

        // 알림 타입 결정
        NotificationType notificationType = switch (event.getType()) {
            case ASSAULT, BURGLARY -> NotificationType.ALERT;
            case DUMP, SWOON, VANDALISM -> NotificationType.WARNING;
        };

        String title = getEventTitle(event.getType());
        String message = String.format("[%s] %s", camera.getAlias(), event.getDescription());

        for (User user : uniqueUsers) {
            createNotification(user.getId(), event.getId(), notificationType, title, message);
        }
        log.info("이벤트 알림 생성 완료: eventId={}, users={}", event.getId(), uniqueUsers.size());
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

    private NotificationDto toNotificationDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId().toString())
                .type(notification.getType().getValue())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .timestamp(notification.getCreatedAt().toString())
                .read(notification.getRead())
                .eventId(notification.getEvent() != null ? notification.getEvent().getId().toString() : null)
                .build();
    }
}

