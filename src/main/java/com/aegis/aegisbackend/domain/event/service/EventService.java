package com.aegis.aegisbackend.domain.event.service;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.notification.service.NotificationService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.event.repository.EventSpecification;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.infra.agent.service.PendingActionService;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final PendingActionService pendingActionService;
    private final S3Service s3Service;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 이벤트 목록 조회 (필터링 + 페이지네이션)
     */
    @Transactional(readOnly = true)
    public PageResponse<EventDto> getEventsFiltered(
            UUID userId,
            List<String> risks,
            List<String> types,
            List<String> statuses,
            List<String> cameraIds,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int size) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);

        // _empty 마커가 있으면 결과 없음 반환 (전체 해제)
        if ((risks != null && risks.contains("_empty")) ||
            (types != null && types.contains("_empty")) ||
            (statuses != null && statuses.contains("_empty")) ||
            (cameraIds != null && cameraIds.contains("_empty"))) {
            return PageResponse.empty(pageable);
        }

        // 문자열을 Enum으로 변환
        List<EventRisk> riskEnums = risks != null && !risks.isEmpty()
                ? risks.stream().map(r -> EventRisk.valueOf(r.toUpperCase())).toList()
                : null;
        List<EventType> typeEnums = types != null && !types.isEmpty()
                ? types.stream().map(t -> EventType.valueOf(t.toUpperCase())).toList()
                : null;
        List<EventStatus> statusEnums = statuses != null && !statuses.isEmpty()
                ? statuses.stream().map(s -> EventStatus.valueOf(s.toUpperCase())).toList()
                : null;

        // 일반 사용자는 할당된 카메라만 조회 가능
        List<UUID> assignedCameraIds = null;
        if (user.getRole() != UserRole.ADMIN) {
            assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
        }

        // 카메라 ID를 UUID로 변환
        List<UUID> filterCameraIds = cameraIds != null && !cameraIds.isEmpty()
                ? cameraIds.stream().map(UUID::fromString).toList()
                : null;

        Page<Event> eventPage = eventRepository.findAll(
                EventSpecification.withFilters(riskEnums, typeEnums, statusEnums, filterCameraIds, startDate, endDate, assignedCameraIds),
                pageable);

        return PageResponse.from(eventPage, EventDto::from);
    }

    @Transactional(readOnly = true)
    public EventDto getEventById(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        // pending 여부를 확인하여 ActionDto 생성
        List<EventDto.ActionDto> actionDtos = null;
        if (event.getActions() != null) {
            actionDtos = event.getActions().stream()
                    .map(action -> {
                        boolean isPending = pendingActionService.isPending(action.getId());
                        return EventDto.ActionDto.from(action, isPending);
                    })
                    .toList();
        }

        return EventDto.builder()
                .id(event.getId().toString())
                .cameraId(event.getCamera().getId().toString())
                .cameraName(event.getCamera().getName())
                .cameraLocation(event.getCamera().getLocation())
                .risk(event.getRisk().getValue())
                .type(event.getType().getValue())
                .occurredAt(event.getOccurredAt().toString())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .report(event.getReport())
                .status(event.getStatus().getValue())
                .actions(actionDtos)
                .build();
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
