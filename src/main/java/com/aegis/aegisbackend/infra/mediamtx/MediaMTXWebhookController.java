package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.stream.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MediaMTX 컨트롤러 (내부망 전용)
 * - 카메라 추가/삭제 알림 수신 → 동기화 트리거
 * - 스트림 인증 검증 (Basic Auth + JWT)
 */
@Slf4j
@RestController
@RequestMapping("/internal/mediamtx")
@RequiredArgsConstructor
public class MediaMTXWebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final CameraRepository cameraRepository;
    private final UserCameraRepository userCameraRepository;

    /**
     * 카메라 동기화 트리거 (단일 엔드포인트)
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Boolean>> handleSyncTrigger(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("MediaMTX 동기화 트리거: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 스트림 인증 검증 (Basic Auth + JWT)
     * - password 필드에서 JWT 추출
     * - JWT 검증 후 사용자 카메라 접근 권한 확인
     */
    @PostMapping("/auth")
    public ResponseEntity<?> validateAuth(@RequestBody MediaMTXAuthRequest request) {
        String path = request.getPath();
        String action = request.getAction();
        String protocol = request.getProtocol();

        // publish 액션은 MediaMTX 내부 인증 사용 (authInternalUsers)
        if ("publish".equals(action)) {
            log.debug("MediaMTX publish 인증: path={}, 내부 인증 사용", path);
            return ResponseEntity.ok().build();
        }

        // 내부 프로토콜(rtsp, hls)은 인증 없이 통과 (MediaMTX 내부 사용)
        if ("rtsp".equals(protocol) || "hls".equals(protocol)) {
            log.debug("MediaMTX 내부 프로토콜 인증: path={}, protocol={}, 통과", path, protocol);
            return ResponseEntity.ok().build();
        }

        // Basic Auth의 password 필드에서 JWT 추출
        String jwt = request.getPassword();
        if (jwt == null || jwt.isEmpty()) {
            log.warn("MediaMTX 인증 실패: JWT 없음, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // JWT 검증
        if (!jwtTokenProvider.validateToken(jwt)) {
            log.warn("MediaMTX 인증 실패: 유효하지 않은 JWT, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // JWT에서 userId 추출
        String userIdStr = jwtTokenProvider.getUserId(jwt);
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (Exception e) {
            log.warn("MediaMTX 인증 실패: 잘못된 userId, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 사용자 조회
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("MediaMTX 인증 실패: 사용자 없음, userId={}, path={}", userId, path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userOpt.get();

        // ADMIN은 모든 카메라 접근 가능
        if (user.getRole() == UserRole.ADMIN) {
            log.info("MediaMTX 인증 성공: ADMIN, path={}", path);
            return ResponseEntity.ok().build();
        }

        // USER는 할당된 카메라만 접근 가능 (path = 카메라 name)
        Optional<Camera> cameraOpt = cameraRepository.findByName(path);
        if (cameraOpt.isEmpty()) {
            log.warn("MediaMTX 인증 실패: 카메라 없음, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Camera camera = cameraOpt.get();

        List<UUID> assignedCameraIds = userCameraRepository.findCameraIdsByUserId(userId);
        if (!assignedCameraIds.contains(camera.getId())) {
            log.warn("MediaMTX 인증 실패: 카메라 접근 권한 없음, userId={}, path={}", userId, path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("MediaMTX 인증 성공: userId={}, path={}", userId, path);
        return ResponseEntity.ok().build();
    }
}
