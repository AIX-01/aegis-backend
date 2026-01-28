package com.aegis.aegisbackend.domain.camera.controller;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.camera.service.CameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** 카메라 API */
@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

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
}
