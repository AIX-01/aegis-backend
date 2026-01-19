package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.NotificationDto;
import com.aegis.aegisbackend.entity.Event;
import com.aegis.aegisbackend.entity.Notification;
import com.aegis.aegisbackend.entity.User;
import com.aegis.aegisbackend.entity.enums.NotificationType;
import com.aegis.aegisbackend.exception.AegisException;
import com.aegis.aegisbackend.exception.ErrorCode;
import com.aegis.aegisbackend.repository.EventRepository;
import com.aegis.aegisbackend.repository.NotificationRepository;
import com.aegis.aegisbackend.repository.UserRepository;
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
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

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

        notificationRepository.save(notification);
        log.debug("Notification created for user: {}", userId);
    }

    @Transactional
    public NotificationDto markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AegisException(ErrorCode.NOTIFICATION_NOT_FOUND));

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
            throw new AegisException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notificationRepository.deleteById(notificationId);
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

