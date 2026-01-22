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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 이벤트 API
 * - 위험/이상 상황 이벤트 조회 및 관리
 * - 클립 다운로드 및 스트리밍 재생
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

    @PostMapping
    public ResponseEntity<EventDto> createEvent(@RequestBody EventDto.CreateRequest request) {
        EventDto event = eventService.createEvent(request);
        return ResponseEntity.ok(event);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EventDto> updateEventStatus(
            @PathVariable UUID id,
            @RequestBody EventDto.UpdateStatusRequest request) {
        EventDto event = eventService.updateEventStatus(id, request.getStatus());
        return ResponseEntity.ok(event);
    }

    /**
     * 클립 다운로드 (attachment)
     * - Content-Disposition: attachment → 브라우저가 파일 다운로드
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
     * 클립 스트리밍 재생 (inline)
     * - Content-Disposition: inline → 브라우저가 비디오 재생
     * - Range 요청 지원으로 시크(seek) 가능
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

        // Range 요청 처리 (비디오 시크 지원)
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
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
        }

        // Range 없으면 전체 반환
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                .body(clipData);
    }
}
