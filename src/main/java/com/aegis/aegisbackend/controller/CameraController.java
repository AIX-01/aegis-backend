package com.aegis.aegisbackend.controller;

import com.aegis.aegisbackend.dto.CameraDto;
import com.aegis.aegisbackend.service.CameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

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
}
