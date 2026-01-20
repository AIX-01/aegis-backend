package com.aegis.aegisbackend.domain.event.controller;

import com.aegis.aegisbackend.domain.event.dto.EventDto;
import com.aegis.aegisbackend.domain.event.service.EventService;
import com.aegis.aegisbackend.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
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

    @GetMapping("/{id}/clip")
    public ResponseEntity<byte[]> getEventClip(@PathVariable UUID id) {
        byte[] clipData = s3Service.downloadEventClip(id);
        if (clipData == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clip.mp4\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(clipData);
    }
}
