package com.aegis.aegisbackend.controller;

import com.aegis.aegisbackend.service.MediaMTXSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final MediaMTXSyncService mediaMTXSyncService;

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
}

