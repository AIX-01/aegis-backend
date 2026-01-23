package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.stream.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.domain.stream.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MediaMTX 컨트롤러 (내부망 전용)
 * - 카메라 추가/삭제 알림 수신 → 동기화 트리거
 * - 스트림 인증 검증
 */
@Slf4j
@RestController
@RequestMapping("/internal/mediamtx")
@RequiredArgsConstructor
public class MediaMTXWebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;
    private final StreamService streamService;

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
}
