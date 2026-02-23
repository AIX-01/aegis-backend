package com.aegis.aegisbackend.domain.event.controller;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.event.service.EventService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 API
 * - 이벤트 조회/삭제 (페이지네이션 지원)
 * - 클립 다운로드/스트리밍
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final S3Service s3Service;

    /**
     * 이벤트 목록 조회 (필터링 + 페이지네이션)
     */
    @GetMapping
    public ResponseEntity<?> getEvents(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> risks,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> cameraIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        PageResponse<EventDto> events = eventService.getEventsFiltered(
                userId, risks, types, statuses, cameraIds, startDate, endDate, page, size);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDto> getEventById(@PathVariable UUID id) {
        EventDto event = eventService.getEventById(id);
        return ResponseEntity.ok(event);
    }

    /**
     * 이벤트 보고서 HTML 조회
     */
    @GetMapping(value = "/{id}/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getEventReport(@PathVariable UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getReport() == null || event.getReport().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(event.getReport());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "이벤트가 삭제되었습니다."));
    }

    /**
     * 클립 재생용 presigned URL 반환
     */
    @GetMapping("/{id}/clip-url")
    public ResponseEntity<Map<String, String>> getClipUrl(@PathVariable UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getClipUrl() == null || event.getClipUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String presignedUrl = s3Service.generateDownloadUrl(id);
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }

    /**
     * 클립 다운로드용 presigned URL 반환
     */
    @GetMapping("/{id}/clip/download-url")
    public ResponseEntity<Map<String, String>> getClipDownloadUrl(@PathVariable UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getClipUrl() == null || event.getClipUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String presignedUrl = s3Service.generateDownloadUrl(id);
        return ResponseEntity.ok(Map.of("url", presignedUrl, "filename", "event_" + id + ".mp4"));
    }
}
