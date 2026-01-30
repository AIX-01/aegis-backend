package com.aegis.aegisbackend.domain.camera.service;

import com.aegis.aegisbackend.domain.camera.dto.CameraDto;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.infra.redis.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 카메라 서비스
 * - 카메라 목록 조회 (권한에 따라)
 * - 카메라 정보 수정 (장소, 활성화)
 * - 카메라 변경 시 SSE로 실시간 알림
 * - 분석 상태 변경 시 Redis Pub/Sub 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final SseEmitterService sseEmitterService;
    private final RedisTokenService redisTokenService;

    private static final int DEFAULT_PAGE_SIZE = 6;

    @Value("${mediamtx.webrtc-url:/stream}")
    private String webrtcBaseUrl;

    @Transactional(readOnly = true)
    public List<CameraDto> getAllCameras(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Camera> cameras = user.getRole() == UserRole.ADMIN
                ? cameraRepository.findAll()
                : cameraRepository.findByIdIn(userCameraRepository.findCameraIdsByUserId(userId));

        // 정렬: 1) connected DESC, 2) enabled DESC, 3) location ASC
        return cameras.stream()
                .sorted(Comparator
                        .comparing(Camera::getConnected, Comparator.reverseOrder())
                        .thenComparing(Camera::getEnabled, Comparator.reverseOrder())
                        .thenComparing(Camera::getLocation, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    /**
     * 카메라 목록 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public PageResponse<CameraDto> getCamerasPaged(UUID userId, int page, int size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<Camera> cameraPage;

        if (user.getRole() == UserRole.ADMIN) {
            cameraPage = cameraRepository.findAllPaged(pageable);
        } else {
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            cameraPage = cameraRepository.findByIdInPaged(assignedCameraIds, pageable);
        }

        return PageResponse.from(cameraPage, this::toDto);
    }

    @Transactional(readOnly = true)
    public CameraDto getCameraById(UUID cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));
        return toDto(camera);
    }

    @Transactional
    public CameraDto updateCamera(UUID cameraId, CameraDto.UpdateRequest request) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

        boolean analysisStateChanged = false;

        if (request.getLocation() != null) {
            camera.setLocation(request.getLocation());
        }
        if (request.getEnabled() != null) {
            boolean wasAnalysisEnabled = camera.getEnabled() && camera.getAnalysisEnabled();
            camera.setEnabled(request.getEnabled());
            // enabled=false면 analysisEnabled도 false로 (Option A: 계층적 구조)
            if (!request.getEnabled()) {
                camera.setAnalysisEnabled(false);
            }
            boolean isAnalysisEnabled = camera.getEnabled() && camera.getAnalysisEnabled();
            if (wasAnalysisEnabled != isAnalysisEnabled) {
                analysisStateChanged = true;
            }
        }
        if (request.getAnalysisEnabled() != null) {
            // enabled=true일 때만 analysisEnabled 변경 가능
            if (camera.getEnabled()) {
                boolean wasAnalysisEnabled = camera.getAnalysisEnabled();
                camera.setAnalysisEnabled(request.getAnalysisEnabled());
                if (wasAnalysisEnabled != request.getAnalysisEnabled()) {
                    analysisStateChanged = true;
                }
            }
        }

        cameraRepository.save(camera);
        log.info("카메라 수정: {}", cameraId);

        // 분석 상태가 변경된 경우 Redis에 목록 저장 + Pub/Sub 발행
        if (analysisStateChanged) {
            syncAnalysisCamerasToRedis();
        }

        // SSE로 카메라 업데이트 브로드캐스트
        CameraDto updatedDto = toDto(camera);
        sseEmitterService.broadcastCamera(updatedDto);

        return updatedDto;
    }

    /**
     * 분석 대상 카메라 목록을 Redis에 저장하고 Pub/Sub 알림 발행
     */
    public void syncAnalysisCamerasToRedis() {
        List<Camera> analysisCameras = cameraRepository
                .findByConnectedAndEnabledAndAnalysisEnabled(true, true, true);

        List<Map<String, String>> cameraList = analysisCameras.stream()
                .map(c -> Map.of(
                        "id", c.getId().toString(),
                        "name", c.getName(),
                        "location", c.getLocation()))
                .toList();

        redisTokenService.saveAnalysisCamerasAndNotify(cameraList);
    }


    private CameraDto toDto(Camera camera) {
        return CameraDto.builder()
                .id(camera.getId().toString())
                .name(camera.getName())
                .connected(camera.getConnected())
                .location(camera.getLocation())
                .enabled(camera.getEnabled())
                .analysisEnabled(camera.getAnalysisEnabled())
                .streamUrl(webrtcBaseUrl + "/" + camera.getName() + "/whep")
                .build();
    }
}
