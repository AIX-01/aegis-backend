package com.aegis.aegisbackend.controller;

import com.aegis.aegisbackend.dto.StreamDto.MediaMTXAuthRequest;
import com.aegis.aegisbackend.entity.Camera;
import com.aegis.aegisbackend.repository.CameraRepository;
import com.aegis.aegisbackend.service.FrameBufferService;
import com.aegis.aegisbackend.service.MediaMTXSyncService;
import com.aegis.aegisbackend.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * MediaMTX에서 카메라 추가/삭제 시 호출되는 Webhook
     * 1초 플래그로 중복 호출 방지 후 카메라 목록 동기화
     */
    @PostMapping("/mediamtx")
    public ResponseEntity<Map<String, Boolean>> handleMediaMTXWebhook(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("Received MediaMTX webhook: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * MediaMTX 카메라 추가 이벤트
     */
    @PostMapping("/mediamtx/path-added")
    public ResponseEntity<Map<String, Boolean>> handlePathAdded(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("MediaMTX path added: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * MediaMTX 카메라 삭제 이벤트
     */
    @PostMapping("/mediamtx/path-removed")
    public ResponseEntity<Map<String, Boolean>> handlePathRemoved(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("MediaMTX path removed: {}", payload);
        mediaMTXSyncService.onWebhookReceived();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * MediaMTX 외부 인증 검증
     * 클라이언트가 스트림에 접근할 때 MediaMTX가 이 엔드포인트를 호출하여 토큰 검증
     * 200 OK = 인증 성공, 401 = 인증 실패
     */
    @PostMapping("/mediamtx/auth")
    public ResponseEntity<?> validateStreamAuth(@RequestBody MediaMTXAuthRequest request) {
        log.debug("MediaMTX auth request: action={}, path={}, protocol={}, user={}",
                request.getAction(), request.getPath(), request.getProtocol(), request.getUser());

        boolean isValid = streamService.validateStreamAuth(
                request.getUser(),  // 토큰이 user 필드로 전달됨
                request.getPath(),
                request.getAction()
        );

        if (isValid) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * MediaMTX에서 FFmpeg로 추출한 프레임 수신
     * - 썸네일 저장 (Redis)
     * - VLM 버퍼에 추가 (8장 모이면 VLM 분석 트리거)
     *
     * @param cameraName 카메라 이름 (MediaMTX path name)
     * @param frameData  JPEG 이미지 바이너리
     */
    @PostMapping(value = "/mediamtx/frame/{cameraName}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> receiveFrame(
            @PathVariable String cameraName,
            @RequestBody byte[] frameData) {

        // 1. 카메라 이름으로 DB에서 카메라 조회
        Optional<Camera> cameraOpt = cameraRepository.findByName(cameraName);
        if (cameraOpt.isEmpty()) {
            log.warn("Frame received for unknown camera: {}", cameraName);
            return ResponseEntity.notFound().build();
        }

        Camera camera = cameraOpt.get();

        // 2. 카메라가 활성화 상태인지 확인
        if (!camera.getActive()) {
            log.debug("Frame ignored for inactive camera: {}", cameraName);
            return ResponseEntity.ok(Map.of("processed", false, "reason", "camera_inactive"));
        }

        // 3. 프레임 처리 (썸네일 + VLM 버퍼)
        List<byte[]> vlmFrames = frameBufferService.processFrame(camera.getId(), frameData);

        // 4. 8장 모이면 VLM 분석 트리거 (TODO: VLM 서비스 연동)
        if (vlmFrames != null) {
            log.info("VLM analysis triggered for camera {}: {} frames", cameraName, vlmFrames.size());
            // TODO: vlmService.analyze(camera.getId(), vlmFrames);
        }

        return ResponseEntity.ok(Map.of("processed", true));
    }
}
