package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.stream.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.stream.service.FrameBufferService;
import com.aegis.aegisbackend.infra.mediamtx.MediaMTXSyncService;
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
 * - 카메라 추가/삭제 동기화
 * - 스트림 인증 검증
 * - 프레임 수신
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

    /** 카메라 동기화 (공통) */
    @PostMapping("/mediamtx")
    public ResponseEntity<Map<String, Boolean>> handleWebhook(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.debug("MediaMTX webhook: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 카메라 추가 이벤트 */
    @PostMapping("/mediamtx/path-added")
    public ResponseEntity<Map<String, Boolean>> handlePathAdded(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.info("카메라 추가: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 카메라 삭제 이벤트 */
    @PostMapping("/mediamtx/path-removed")
    public ResponseEntity<Map<String, Boolean>> handlePathRemoved(
            @RequestBody(required = false) Map<String, Object> payload) {
        log.info("카메라 삭제: {}", payload);
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
