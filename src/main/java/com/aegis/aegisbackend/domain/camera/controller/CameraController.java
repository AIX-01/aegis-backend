package com.aegis.aegisbackend.domain.camera.controller;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.stream.dto.StreamDto.StreamAccessResponse;
import com.aegis.aegisbackend.domain.camera.service.CameraService;
import com.aegis.aegisbackend.domain.stream.service.FrameBufferService;
import com.aegis.aegisbackend.domain.stream.service.StreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 카메라 API */
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;
    private final StreamService streamService;
    private final FrameBufferService frameBufferService;

    @GetMapping
    public ResponseEntity<List<CameraDto>> getAll(@AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(cameraService.getAllCameras(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CameraDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cameraService.getCameraById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CameraDto> update(
            @PathVariable UUID id, @RequestBody CameraDto.UpdateRequest request) {
        return ResponseEntity.ok(cameraService.updateCamera(id, request));
    }

    /** 스트림 접근 토큰 발급 */
    @PostMapping("/{id}/stream")
    public ResponseEntity<StreamAccessResponse> requestStream(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(streamService.requestStreamAccess(userId, id));
    }

    /** 썸네일 조회 (JSON) */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<?> getThumbnail(@PathVariable UUID id) {
        String base64 = frameBufferService.getThumbnail(id);
        if (base64 == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("image", base64));
    }

    /** 썸네일 조회 (이미지) */
    @GetMapping(value = "/{id}/thumbnail.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getThumbnailImage(@PathVariable UUID id) {
        String base64 = frameBufferService.getThumbnail(id);
        if (base64 == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Base64.getDecoder().decode(base64));
    }
}
