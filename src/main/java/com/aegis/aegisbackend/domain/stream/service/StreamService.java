package com.aegis.aegisbackend.domain.stream.service;

import com.aegis.aegisbackend.domain.stream.dto.StreamDto.StreamAccessResponse;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.user.entity.User;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 스트림 서비스
 * - WebRTC 스트림 접근 토큰 발급
 * - MediaMTX 외부 인증 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final RedisTokenService redisTokenService;

    @Value("${mediamtx.webrtc-url:/stream}")
    private String webrtcBaseUrl;

    /** 스트림 접근 토큰 발급 */
    @Transactional(readOnly = true)
    public StreamAccessResponse requestStreamAccess(UUID userId, UUID cameraId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMERA_NOT_FOUND));

        validateAccess(user, camera, cameraId);

        String token = redisTokenService.generateStreamToken(userId, cameraId);
        // Caddy 프록시 경로: /stream/cam/whep
        String streamUrl = webrtcBaseUrl + "/" + camera.getName() + "/whep";

        log.info("스트림 접근 허용: userId={}, cameraId={}", userId, cameraId);

        return StreamAccessResponse.builder()
                .streamUrl(streamUrl)
                .token(token)
                .cameraId(cameraId.toString())
                .cameraName(camera.getAlias())
                .build();
    }

    /** MediaMTX 외부 인증 검증 */
    public boolean validateStreamAuth(String token, String path, String action) {
        if ("publish".equals(action)) {
            return true; // publish는 MediaMTX 자체 인증 사용
        }

        if (token == null || token.isEmpty()) {
            log.warn("스트림 인증 실패: 토큰 없음, path={}", path);
            return false;
        }

        String cameraId = redisTokenService.validateAndConsumeStreamToken(token);
        if (cameraId == null) {
            log.warn("스트림 인증 실패: 유효하지 않은 토큰, path={}", path);
            return false;
        }

        log.info("스트림 인증 성공: path={}, cameraId={}", path, cameraId);
        return true;
    }

    // === Private ===

    private void validateAccess(User user, Camera camera, UUID cameraId) {
        // 권한 확인
        if (user.getRole() != UserRole.ADMIN) {
            List<UUID> assigned = userCameraRepository.findCameraIdsByUserId(user.getId());
            if (!assigned.contains(cameraId)) {
                throw new BusinessException(ErrorCode.CAMERA_ACCESS_DENIED);
            }
        }
        // 카메라 연결 상태 확인
        if (!camera.getConnected()) {
            throw new BusinessException(ErrorCode.CAMERA_NOT_CONNECTED);
        }
        // active는 AI 분석 활성화 여부이므로 스트림 시청과 무관
    }
}
