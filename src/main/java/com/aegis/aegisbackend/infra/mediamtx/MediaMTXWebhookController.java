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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MediaMTX 통합 인증 컨트롤러
 * - SRT publish: 외부에서 스트림 수신 (ID/PW 인증)
 * - WebRTC read: 프론트엔드 스트리밍 (JWT 인증)
 * - RTSP/HLS read: 내부용 (인증 없음)
 * - 카메라 동기화 트리거
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

    @Value("${mediamtx.srt-user}")
    private String srtUser;

    @Value("${mediamtx.srt-password}")
    private String srtPassword;

    /**
     * 카메라 동기화 트리거
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Boolean>> handleSyncTrigger(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("MediaMTX 동기화 트리거: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * MediaMTX 통합 인증
     * - SRT publish: 고정 ID/PW 인증 (streamid=publish:path:user:password)
     * - RTSP/HLS read: 인증 없이 통과 (내부용)
     * - WebRTC read: JWT 인증 + 카메라 권한 확인
     */
    @PostMapping("/auth")
    public ResponseEntity<?> validateAuth(@RequestBody MediaMTXAuthRequest request) {
        String protocol = request.getProtocol();
        String action = request.getAction();
        String path = request.getPath();

        log.debug("MediaMTX 인증 요청: protocol={}, action={}, path={}", protocol, action, path);

        // 1. RTSP/HLS read → 인증 없이 통과 (내부용)
        if (("rtsp".equals(protocol) || "hls".equals(protocol)) && "read".equals(action)) {
            log.debug("MediaMTX 인증 성공: 내부 프로토콜, protocol={}, path={}", protocol, path);
            return ResponseEntity.ok().build();
        }

        // 2. SRT publish → 고정 ID/PW 확인
        if ("srt".equals(protocol) && "publish".equals(action)) {
            if (srtUser.equals(request.getUser()) && srtPassword.equals(request.getPassword())) {
                log.info("MediaMTX 인증 성공: SRT publish, path={}", path);
                return ResponseEntity.ok().build();
            }
            log.warn("MediaMTX 인증 실패: SRT 인증 정보 불일치, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 3. WebRTC read → JWT 검증 + 카메라 권한 확인
        if ("webrtc".equals(protocol) && "read".equals(action)) {
            return validateWebRtcAuth(request, path);
        }

        // 그 외 → 거부
        log.warn("MediaMTX 인증 실패: 허용되지 않은 요청, protocol={}, action={}, path={}", protocol, action, path);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * WebRTC JWT 인증 및 카메라 권한 확인
     */
    private ResponseEntity<?> validateWebRtcAuth(MediaMTXAuthRequest request, String path) {
        String jwt = request.getPassword();
        if (jwt == null || jwt.isEmpty()) {
            log.warn("MediaMTX 인증 실패: JWT 없음, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!jwtTokenProvider.validateToken(jwt)) {
            log.warn("MediaMTX 인증 실패: 유효하지 않은 JWT, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userIdStr = jwtTokenProvider.getUserId(jwt);
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (Exception e) {
            log.warn("MediaMTX 인증 실패: 잘못된 userId, path={}", path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("MediaMTX 인증 실패: 사용자 없음, userId={}, path={}", userId, path);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userOpt.get();

        // ADMIN은 모든 카메라 접근 가능
        if (user.getRole() == UserRole.ADMIN) {
            log.info("MediaMTX 인증 성공: ADMIN WebRTC, path={}", path);
            return ResponseEntity.ok().build();
        }

        // USER는 할당된 카메라만 접근 가능
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

        log.info("MediaMTX 인증 성공: USER WebRTC, userId={}, path={}", userId, path);
        return ResponseEntity.ok().build();
    }
}
