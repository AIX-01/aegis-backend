package com.aegis.aegisbackend.domain.event.controller;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.event.service.EventService;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 API
 * - 이벤트 조회/삭제
 * - 클립 다운로드/스트리밍
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final S3Service s3Service;

    @GetMapping
    public ResponseEntity<List<EventDto>> getAllEvents(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<EventDto> events = eventService.getAllEvents(userId);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDto> getEventById(@PathVariable UUID id) {
        EventDto event = eventService.getEventById(id);
        return ResponseEntity.ok(event);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "이벤트가 삭제되었습니다."));
    }

    /**
     * 클립 다운로드
     */
    @GetMapping("/{id}/clip")
    public ResponseEntity<byte[]> downloadClip(@PathVariable UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getClipUrl() == null || event.getClipUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] clipData = s3Service.downloadClip(event.getClipUrl());
        if (clipData == null) {
            return ResponseEntity.notFound().build();
        }

        String filename = "event_" + id + ".mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(clipData);
    }

    /**
     * 클립 스트리밍 (Range 지원)
     */
    @GetMapping("/{id}/clip/stream")
    public ResponseEntity<byte[]> streamClip(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (event.getClipUrl() == null || event.getClipUrl().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] clipData = s3Service.downloadClip(event.getClipUrl());
        if (clipData == null) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = clipData.length;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            try {
                String[] ranges = rangeHeader.substring(6).split("-");
                long start = Long.parseLong(ranges[0]);
                long end = ranges.length > 1 && !ranges[1].isEmpty()
                        ? Long.parseLong(ranges[1])
                        : fileSize - 1;

                if (start >= fileSize) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                            .build();
                }

                end = Math.min(end, fileSize - 1);
                long contentLength = end - start + 1;

                byte[] partialData = new byte[(int) contentLength];
                System.arraycopy(clipData, (int) start, partialData, 0, (int) contentLength);

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .body(partialData);
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                .body(clipData);
    }
}
