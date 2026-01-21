package com.aegis.aegisbackend.domain.camera.service;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.AegisException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 카메라 서비스
 * - 카메라 목록 조회 (권한에 따라)
 * - 카메라 정보 수정 (별칭, 활성화)
 * - 카메라 변경 시 SSE로 실시간 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional(readOnly = true)
    public List<CameraDto> getAllCameras(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        List<Camera> cameras = user.getRole() == UserRole.ADMIN
                ? cameraRepository.findAll()
                : cameraRepository.findByIdIn(userCameraRepository.findCameraIdsByUserId(userId));

        return cameras.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CameraDto getCameraById(UUID cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));
        return toDto(camera);
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
        log.info("카메라 수정: {}", cameraId);

        // SSE로 카메라 업데이트 브로드캐스트
        CameraDto updatedDto = toDto(camera);
        sseEmitterService.broadcastCamera(updatedDto);

        return updatedDto;
    }

    @Transactional(readOnly = true)
    public long countActiveCameras() {
        return cameraRepository.findByConnectedAndActive(true, true).size();
    }

    @Transactional(readOnly = true)
    public long countTotalCameras() {
        return cameraRepository.count();
    }

    private CameraDto toDto(Camera camera) {
        return CameraDto.builder()
                .id(camera.getId().toString())
                .name(camera.getName())
                .connected(camera.getConnected())
                .alias(camera.getAlias())
                .active(camera.getActive())
                .build();
    }
}
