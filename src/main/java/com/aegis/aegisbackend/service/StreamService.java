package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.StreamDto.StreamAccessResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final CameraRepository cameraRepository;
    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final RedisTokenService redisTokenService;

    @Value("${mediamtx.webrtc-url:http://localhost:8889}")
    private String webrtcBaseUrl;

    /**
     * 사용자가 특정 카메라의 스트림에 접근하기 위한 토큰 발급
     */
    @Transactional(readOnly = true)
    public StreamAccessResponse requestStreamAccess(UUID userId, UUID cameraId) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        // 2. 카메라 조회
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new AegisException(ErrorCode.CAMERA_NOT_FOUND));

        // 3. 권한 확인 (Admin이거나 카메라가 할당된 사용자)
        if (user.getRole() != UserRole.ADMIN) {
            List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
            if (!assignedCameraIds.contains(cameraId)) {
                throw new AegisException(ErrorCode.CAMERA_ACCESS_DENIED);
            }
        }

        // 4. 카메라 연결 상태 확인
        if (!camera.getConnected()) {
            throw new AegisException(ErrorCode.CAMERA_NOT_CONNECTED);
        }

        // 5. 카메라 활성화 상태 확인
        if (!camera.getActive()) {
            throw new AegisException(ErrorCode.CAMERA_NOT_ACTIVE);
        }

        // 6. 일회용 스트림 토큰 생성 (30초 TTL)
        String streamToken = redisTokenService.generateStreamToken(userId, cameraId);

        // 7. WebRTC URL 생성
        String streamUrl = String.format("%s/%s/whep", webrtcBaseUrl, camera.getName());

        log.info("Stream access granted: userId={}, cameraId={}, token={}", userId, cameraId, streamToken);

        return StreamAccessResponse.builder()
                .streamUrl(streamUrl)
                .token(streamToken)
                .cameraId(cameraId.toString())
                .cameraName(camera.getAlias())
                .build();
    }

    /**
     * MediaMTX 외부 인증 검증
     * @return true if valid, false otherwise
     */
    public boolean validateStreamAuth(String token, String path, String action) {
        // publish 요청은 별도 인증 (aegis/trillion)
        if ("publish".equals(action)) {
            log.debug("Publish action - skipping token validation for path: {}", path);
            return true; // MediaMTX publishUser/publishPass로 처리됨
        }

        // read 요청은 토큰 검증
        if (token == null || token.isEmpty()) {
            log.warn("Stream auth failed: no token provided for path: {}", path);
            return false;
        }

        // 토큰 검증 (일회용이므로 소비)
        String cameraId = redisTokenService.validateAndConsumeStreamToken(token);
        if (cameraId == null) {
            log.warn("Stream auth failed: invalid or expired token for path: {}", path);
            return false;
        }

        log.info("Stream auth success: token validated for path: {}, cameraId: {}", path, cameraId);
        return true;
    }
}
