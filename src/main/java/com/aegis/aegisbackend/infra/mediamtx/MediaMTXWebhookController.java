package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.stream.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.domain.stream.service.FrameBufferService;
import com.aegis.aegisbackend.domain.stream.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MediaMTX 컨트롤러 (내부망 전용)
 * - 카메라 추가/삭제 알림 수신 → 동기화 트리거
 * - 스트림 인증 검증
 * - 프레임 수신
 */
@Slf4j
@RestController
@RequestMapping("/internal/mediamtx")
@RequiredArgsConstructor
public class MediaMTXWebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;
    private final StreamService streamService;
    private final FrameBufferService frameBufferService;
    private final CameraRepository cameraRepository;

    // 카메라 이름 → Camera 로컬 캐시 (초당 프레임 처리 시 DB 조회 최소화)
    private final Map<String, Camera> cameraCache = new ConcurrentHashMap<>();

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

    /** 스트림 인증 검증 */
    @PostMapping("/auth")
    public ResponseEntity<?> validateAuth(@RequestBody MediaMTXAuthRequest request) {
        String path = request.getPath();
        String query = request.getQuery();
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

        // WebRTC read 요청만 토큰 검증
        String token = request.getJwt();
        if (token == null || token.isEmpty()) {
            // query에서 token= 파라미터 추출
            if (query != null && !query.isEmpty()) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                }
            }
        }

        log.info("MediaMTX 인증 요청: path={}, action={}, protocol={}, token={}",
                path, action, protocol,
                token != null ? "있음" : "없음");

        boolean valid = streamService.validateStreamAuth(token, path, action);
        return valid ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** 프레임 수신 (AI 버퍼: enabled && analysisEnabled인 카메라만) */
    @PostMapping(value = "/frame/{cameraName}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> receiveFrame(
            @PathVariable String cameraName,
            @RequestBody byte[] frameData) {

        // 캐시 우선 조회, 미스 시 DB 조회
        Camera camera = cameraCache.computeIfAbsent(cameraName, name ->
            cameraRepository.findByName(name).orElse(null)
        );

        // DB 미등록 카메라는 거부
        if (camera == null) {
            return ResponseEntity.notFound().build();
        }

        // AI 버퍼는 enabled && analysisEnabled인 카메라만
        boolean shouldAnalyze = camera.getEnabled() && camera.getAnalysisEnabled();
        frameBufferService.processFrame(camera.getId(), frameData, shouldAnalyze);

        return ResponseEntity.ok(Map.of("processed", true, "analysisEnabled", shouldAnalyze));
    }

    /** 전체 캐시 무효화 */
    public void invalidateCameraCache() {
        cameraCache.clear();
        log.debug("카메라 캐시 무효화");
    }
}
