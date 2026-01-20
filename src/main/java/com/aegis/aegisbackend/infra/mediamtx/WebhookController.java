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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MediaMTX Webhook 컨트롤러
 * - 카메라 추가/삭제 알림 수신 → 동기화 트리거
 * - 스트림 인증 검증
 * - 프레임 수신
 *
 * 동기화 방식:
 * - MediaMTX에서 pathAdded/pathRemoved 웹훅 수신 시 알림만 받음
 * - Redis 1초 잠금으로 중복 요청 방지
 * - Spring이 MediaMTX API에 전체 목록 요청하여 DB와 동기화
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;
    private final StreamService streamService;
    private final FrameBufferService frameBufferService;
    private final CameraRepository cameraRepository;

    /**
     * 카메라 동기화 트리거 (단일 엔드포인트)
     * - MediaMTX pathAdded/pathRemoved 웹훅 모두 이 엔드포인트로 수신
     * - 알림만 받고, 실제 동기화는 MediaMTX API 조회로 처리
     * - Redis 1초 잠금으로 연속 요청 병합
     */
    @PostMapping("/mediamtx/sync")
    public ResponseEntity<Map<String, Boolean>> handleSyncTrigger(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("MediaMTX 동기화 트리거: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 스트림 인증 검증 */
    @PostMapping("/mediamtx/auth")
    public ResponseEntity<?> validateAuth(@RequestBody MediaMTXAuthRequest request) {
        boolean valid = streamService.validateStreamAuth(
                request.getUser(), request.getPath(), request.getAction());
        return valid ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** 프레임 수신 (썸네일 + VLM 버퍼) */
    @PostMapping(value = "/mediamtx/frame/{cameraName}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> receiveFrame(
            @PathVariable String cameraName,
            @RequestBody byte[] frameData) {

        Optional<Camera> cameraOpt = cameraRepository.findByName(cameraName);
        if (cameraOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Camera camera = cameraOpt.get();
        if (!camera.getActive()) {
            return ResponseEntity.ok(Map.of("processed", false, "reason", "inactive"));
        }

        List<byte[]> vlmFrames = frameBufferService.processFrame(camera.getId(), frameData);
        if (vlmFrames != null) {
            log.info("VLM 분석 트리거: camera={}, frames={}", cameraName, vlmFrames.size());
            // TODO: vlmService.analyze(camera.getId(), vlmFrames)
        }

        return ResponseEntity.ok(Map.of("processed", true));
    }
}
