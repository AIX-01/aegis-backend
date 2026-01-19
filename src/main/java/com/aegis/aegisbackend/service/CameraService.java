package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.CameraDto;
import com.aegis.aegisbackend.entity.Camera;
import com.aegis.aegisbackend.entity.User;
import com.aegis.aegisbackend.entity.enums.UserRole;
import com.aegis.aegisbackend.exception.AegisException;
import com.aegis.aegisbackend.exception.ErrorCode;
import com.aegis.aegisbackend.repository.CameraRepository;
import com.aegis.aegisbackend.repository.UserCameraRepository;
import com.aegis.aegisbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;

    @Transactional(readOnly = true)
    public List<CameraDto> getAllCameras(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        List<Camera> cameras;

        if (user.getRole() == UserRole.ADMIN) {
            // Admin은 모든 카메라 조회
            cameras = cameraRepository.findAll();
        } else {
            // 일반 사용자는 할당된 카메라만 조회
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            cameras = cameraRepository.findByIdIn(assignedCameraIds);
        }

        return cameras.stream()
                .map(this::toCameraDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CameraDto getCameraById(UUID cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));

        return toCameraDto(camera);
    }

    @Transactional
    public CameraDto updateCamera(UUID cameraId, CameraDto.UpdateRequest request) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));

        if (request.getAlias() != null) {
            camera.setAlias(request.getAlias());
        }
        if (request.getActive() != null) {
            camera.setActive(request.getActive());
        }

        cameraRepository.save(camera);
        log.info("Camera updated: {}", cameraId);

        return toCameraDto(camera);
    }

    @Transactional(readOnly = true)
    public long countActiveCameras() {
        return cameraRepository.findByConnectedAndActive(true, true).size();
    }

    @Transactional(readOnly = true)
    public long countTotalCameras() {
        return cameraRepository.count();
    }

    private CameraDto toCameraDto(Camera camera) {
        return CameraDto.builder()
                .id(camera.getId().toString())
                .name(camera.getName())
                .connected(camera.getConnected())
                .alias(camera.getAlias())
                .active(camera.getActive())
                .build();
    }
}

