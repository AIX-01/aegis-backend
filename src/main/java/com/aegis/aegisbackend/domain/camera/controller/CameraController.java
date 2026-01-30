package com.aegis.aegisbackend.domain.camera.controller;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.camera.service.CameraService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 카메라 API
 * - 카메라 목록 조회 (페이지네이션)
 * - 카메라 정보 수정
 */
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    /**
     * 카메라 목록 조회 (페이지네이션)
     */
    @GetMapping
    public ResponseEntity<?> getAll(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        UUID userId = UUID.fromString(user.getUsername());
        PageResponse<CameraDto> cameras = cameraService.getCamerasPaged(userId, page, size);
        return ResponseEntity.ok(cameras);
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
}
