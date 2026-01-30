package com.aegis.aegisbackend.domain.event.service;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final S3Service s3Service;

    private static final int DEFAULT_PAGE_SIZE = 20;

    @Transactional(readOnly = true)
    public List<EventDto> getAllEvents(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Event> events;

        if (user.getRole() == UserRole.ADMIN) {
            events = eventRepository.findAllWithCamera();
        } else {
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            events = eventRepository.findByCameraIdInWithCamera(assignedCameraIds);
        }

        return events.stream()
                .map(EventDto::from)
                .toList();
    }

    /**
     * 이벤트 목록 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public PageResponse<EventDto> getEventsPaged(UUID userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<Event> eventPage;

        if (user.getRole() == UserRole.ADMIN) {
            eventPage = eventRepository.findAllWithCameraPaged(pageable);
        } else {
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            eventPage = eventRepository.findByCameraIdInWithCameraPaged(assignedCameraIds, pageable);
        }

        return PageResponse.from(eventPage, EventDto::from);
    }

    @Transactional(readOnly = true)
    public EventDto getEventById(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        return EventDto.from(event);
    }

    /**
     * 이벤트 삭제 (Admin 전용)
     */
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // S3에서 클립 삭제
        if (event.getClipUrl() != null && !event.getClipUrl().isEmpty()) {
            try {
                s3Service.deleteClip(event.getClipUrl());
                log.info("이벤트 클립 삭제 완료: eventId={}, clipUrl={}", eventId, event.getClipUrl());
            } catch (Exception e) {
                log.warn("이벤트 클립 삭제 실패: eventId={}, error={}", eventId, e.getMessage());
            }
        }

        // 연관 알림 삭제
        notificationService.deleteNotificationsByEventId(eventId);

        // 이벤트 삭제
        eventRepository.delete(event);
        log.info("이벤트 삭제 완료: eventId={}", eventId);

        // SSE 브로드캐스트
        sseEmitterService.broadcastEventDeleted(eventId.toString());
    }
}
