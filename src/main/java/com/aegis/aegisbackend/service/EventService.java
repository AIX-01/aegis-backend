package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.EventDto;
import com.aegis.aegisbackend.entity.Camera;
import com.aegis.aegisbackend.entity.Event;
import com.aegis.aegisbackend.entity.User;
import com.aegis.aegisbackend.entity.enums.EventStatus;
import com.aegis.aegisbackend.entity.enums.EventType;
import com.aegis.aegisbackend.entity.enums.NotificationType;
import com.aegis.aegisbackend.entity.enums.UserRole;
import com.aegis.aegisbackend.exception.AegisException;
import com.aegis.aegisbackend.exception.ErrorCode;
import com.aegis.aegisbackend.repository.CameraRepository;
import com.aegis.aegisbackend.repository.EventRepository;
import com.aegis.aegisbackend.repository.UserCameraRepository;
import com.aegis.aegisbackend.repository.UserRepository;
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
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public List<EventDto> getAllEvents(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        List<Event> events;

        if (user.getRole() == UserRole.ADMIN) {
            events = eventRepository.findAllWithCamera();
        } else {
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            events = eventRepository.findByCameraIdInWithCamera(assignedCameraIds);
        }

        return events.stream()
                .map(this::toEventDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDto getEventById(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AegisException(ErrorCode.EVENT_NOT_FOUND));

        return toEventDto(event);
    }

    @Transactional
    public EventDto createEvent(EventDto.CreateRequest request) {
        Camera camera = cameraRepository.findById(UUID.fromString(request.getCameraId()))
                .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));

        Event event = Event.builder()
                .camera(camera)
                .type(EventType.fromValue(request.getType()))
                .timestamp(LocalDateTime.parse(request.getTimestamp()))
                .status(EventStatus.PROCESSING)
                .description(request.getDescription())
                .aiAction(request.getAiAction())
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
        createNotificationsForEvent(savedEvent);

        return toEventDto(savedEvent);
    }

    @Transactional
    public EventDto updateEventStatus(UUID eventId, String status) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AegisException(ErrorCode.EVENT_NOT_FOUND));

        event.setStatus(EventStatus.fromValue(status));
        eventRepository.save(event);
        log.info("Event {} status updated to: {}", eventId, status);

        return toEventDto(event);
    }

    /**
     * 이벤트 발생 시 관련 사용자들에게 알림 생성
     */
    private void createNotificationsForEvent(Event event) {
        Camera camera = event.getCamera();

        // 해당 카메라에 할당된 사용자들 조회
        List<User> assignedUsers = userRepository.findUsersByCameraId(camera.getId());

        // Admin 사용자들 추가
        List<User> admins = userRepository.findByRole(UserRole.ADMIN);

        // 중복 제거
        assignedUsers.addAll(admins);
        List<User> uniqueUsers = assignedUsers.stream().distinct().toList();

        // 알림 타입 결정
        NotificationType notificationType = switch (event.getType()) {
            case ASSAULT, THEFT -> NotificationType.ALERT;
            case SUSPICIOUS -> NotificationType.WARNING;
            case NORMAL -> NotificationType.INFO;
        };

        // 알림 제목 및 메시지 생성
        String title = getEventTitle(event.getType());
        String message = String.format("[%s] %s", camera.getAlias(), event.getDescription());

        // 각 사용자에게 알림 생성
        for (User user : uniqueUsers) {
            notificationService.createNotification(
                    user.getId(),
                    event.getId(),
                    notificationType,
                    title,
                    message
            );
        }

        log.info("Created {} notifications for event: {}", uniqueUsers.size(), event.getId());
    }

    private String getEventTitle(EventType type) {
        return switch (type) {
            case ASSAULT -> "폭행 감지";
            case THEFT -> "절도 감지";
            case SUSPICIOUS -> "의심 행동 감지";
            case NORMAL -> "정상 활동";
        };
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
                .aiAction(event.getAiAction())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .analysisReport(event.getAnalysisReport())
                .build();
    }
}

