package com.aegis.aegisbackend.domain.camera.controller;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.camera.service.CameraService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 카메라 API
 * - 카메라 목록 조회 (페이지네이션)
 * - 카메라 전체 목록 조회 (멤버 관리용)
 * - 카메라 정보 수정
 */
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    /**
     * 카메라 목록 조회 (페이지네이션)
     * 정렬: connected DESC → enabled DESC → location ASC
     */
    @GetMapping
    public ResponseEntity<PageResponse<CameraDto>> getAll(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(cameraService.getCamerasPaged(userId, page, size));
    }

    /**
     * 카메라 전체 목록 조회 (멤버 관리 - 카메라 할당용)
     */
    @GetMapping("/all")
    public ResponseEntity<List<CameraDto>> getAllCameras(@AuthenticationPrincipal UserDetails user) {
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
}
