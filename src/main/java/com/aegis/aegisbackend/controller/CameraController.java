package com.aegis.aegisbackend.controller;

import com.aegis.aegisbackend.dto.CameraDto;
import com.aegis.aegisbackend.dto.StreamDto.StreamAccessResponse;
import com.aegis.aegisbackend.service.CameraService;
import com.aegis.aegisbackend.service.FrameBufferService;
import com.aegis.aegisbackend.service.StreamService;
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

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;
    private final StreamService streamService;
    private final FrameBufferService frameBufferService;

    @GetMapping
    public ResponseEntity<List<CameraDto>> getAllCameras(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        List<CameraDto> cameras = cameraService.getAllCameras(userId);
        return ResponseEntity.ok(cameras);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CameraDto> getCameraById(@PathVariable UUID id) {
        CameraDto camera = cameraService.getCameraById(id);
        return ResponseEntity.ok(camera);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CameraDto> updateCamera(
            @PathVariable UUID id,
            @RequestBody CameraDto.UpdateRequest request) {
        CameraDto camera = cameraService.updateCamera(id, request);
        return ResponseEntity.ok(camera);
    }

    @PostMapping("/{id}/stream")
    public ResponseEntity<StreamAccessResponse> requestStreamAccess(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        StreamAccessResponse response = streamService.requestStreamAccess(userId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * 카메라 썸네일 조회 (Base64 이미지)
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<?> getThumbnail(@PathVariable UUID id) {
        String base64Image = frameBufferService.getThumbnail(id);
        if (base64Image == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("image", base64Image));
    }

    /**
     * 카메라 썸네일 조회 (바이너리 이미지)
     */
    @GetMapping(value = "/{id}/thumbnail.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getThumbnailImage(@PathVariable UUID id) {
        String base64Image = frameBufferService.getThumbnail(id);
        if (base64Image == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        return ResponseEntity.ok(imageBytes);
    }
}
