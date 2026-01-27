package com.aegis.aegisbackend.domain.event.service;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.infra.s3.S3Service;
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
public class EventService {

    private final EventRepository eventRepository;
    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final S3Service s3Service;

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

    @Transactional(readOnly = true)
    public EventDto getEventById(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        return EventDto.from(event);
    }


    /**
     * 이벤트에 클립 URL 업데이트
     */
    @Transactional
    public void updateClipUrl(UUID eventId, String clipUrl) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        event.setClipUrl(clipUrl);
        event.setStatus(EventStatus.RESOLVED);
        eventRepository.save(event);
        log.info("이벤트 클립 업데이트: eventId={}, clipUrl={}", eventId, clipUrl);
    }

    @Transactional
    public EventDto createEvent(EventDto.CreateRequest request) {
        Camera camera = cameraRepository.findById(UUID.fromString(request.getCameraId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

        Event event = Event.builder()
                .camera(camera)
                .type(EventType.fromValue(request.getType()))
                .timestamp(LocalDateTime.parse(request.getTimestamp()))
                .status(EventStatus.PROCESSING)
                .description(request.getDescription())
                .agentAction(request.getAgentAction())
                .summary(request.getSummary())
                .analysisReport(request.getAnalysisReport())
                .build();

        Event savedEvent = eventRepository.save(event);
        log.info("Event created: {} for camera: {}", savedEvent.getId(), camera.getName());

        // 클립 업로드 처리
        if (request.getClipData() != null && request.getClipData().length > 0) {
            String clipKey = s3Service.uploadEventClip(savedEvent.getId(), request.getClipData(), "video/mp4");
            savedEvent.setClipUrl(clipKey);
            eventRepository.save(savedEvent);
        }

        // 이벤트 발생 시 관련 사용자들에게 알림 생성
        notificationService.createNotificationsForEvent(savedEvent);

        // SSE로 이벤트 생성 브로드캐스트
        EventDto eventDto = EventDto.from(savedEvent);
        sseEmitterService.broadcastEvent(eventDto);

        return eventDto;
    }

    @Transactional
    public EventDto updateEventStatus(UUID eventId, String status) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        event.setStatus(EventStatus.fromValue(status));
        eventRepository.save(event);
        log.info("Event {} status updated to: {}", eventId, status);

        // SSE로 이벤트 상태 변경 브로드캐스트
        EventDto eventDto = EventDto.from(event);
        sseEmitterService.broadcastEvent(eventDto);

        return eventDto;
    }

    /**
     * 이벤트 삭제 (Admin 전용)
     * - S3에서 클립 삭제
     * - DB에서 연관 알림 삭제
     * - DB에서 이벤트 삭제
     */
    @Transactional
    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // 1. S3에서 클립 삭제
        if (event.getClipUrl() != null && !event.getClipUrl().isEmpty()) {
            try {
                s3Service.deleteClip(event.getClipUrl());
                log.info("이벤트 클립 삭제 완료: eventId={}, clipUrl={}", eventId, event.getClipUrl());
            } catch (Exception e) {
                log.warn("이벤트 클립 삭제 실패 (이벤트는 삭제 진행): eventId={}, error={}", eventId, e.getMessage());
            }
        }

        // 2. 연관 알림 삭제 (Event 엔티티의 notifications 관계로 cascade 처리)
        // Event 엔티티에 @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) 설정 필요
        // 또는 직접 삭제
        notificationService.deleteNotificationsByEventId(eventId);

        // 3. 이벤트 삭제
        eventRepository.delete(event);
        log.info("이벤트 삭제 완료: eventId={}", eventId);

        // SSE로 이벤트 삭제 브로드캐스트
        sseEmitterService.broadcastEventDeleted(eventId.toString());
    }
}
