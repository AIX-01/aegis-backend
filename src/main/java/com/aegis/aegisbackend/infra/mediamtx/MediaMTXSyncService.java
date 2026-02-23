package com.aegis.aegisbackend.infra.mediamtx;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.camera.service.CameraService;
import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.infra.redis.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MediaMTX 동기화 서비스
 * - Webhook 수신 시 카메라 목록 동기화
 * - 새 카메라는 비활성화 상태로 추가
 * - 연결 해제된 카메라는 오프라인 처리
 * - 동기화 완료 시 SSE로 프론트엔드에 알림
 * - Redis에 분석 대상 카메라 목록 저장 + Pub/Sub으로 Python Agent에 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMTXSyncService {

    private final CameraRepository cameraRepository;
    private final CameraService cameraService;
    private final RedisTokenService redisTokenService;
    private final WebClient.Builder webClientBuilder;
    private final SseEmitterService sseEmitterService;

    @Value("${mediamtx.api-url}")
    private String mediaMtxApiUrl;

    /** Webhook 수신 시 호출 (1초 잠금으로 중복 방지) */
    @Async
    public void onWebhookReceived() {
        if (redisTokenService.isSyncLocked()) {
            return;
        }
        if (!redisTokenService.tryAcquireSyncLock()) {
            return;
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        syncCameras();
    }

    /** MediaMTX와 DB 카메라 목록 동기화 */
    @Transactional
    public void syncCameras() {
        log.info("카메라 동기화 시작");

        boolean hasChanges = false;

        try {
            List<String> mtxCameras = fetchCamerasFromMediaMTX();
            Set<String> mtxCameraSet = Set.copyOf(mtxCameras);

            List<Camera> dbCameras = cameraRepository.findAll();
            Set<String> dbCameraNames = dbCameras.stream()
                    .map(Camera::getName)
                    .collect(Collectors.toSet());

            // 새 카메라 추가 (비활성화 상태)
            for (String name : mtxCameras) {
                if (!dbCameraNames.contains(name)) {
                    Camera camera = Camera.builder()
                            .name(name)
                            .location(name)
                            .connected(true)
                            .enabled(false)
                            .analysisEnabled(false)
                            .build();
                    cameraRepository.save(camera);
                    log.info("새 카메라 추가: {}", name);
                    hasChanges = true;
                }
            }

            // 연결 상태 업데이트
            for (Camera camera : dbCameras) {
                boolean connected = mtxCameraSet.contains(camera.getName());
                if (camera.getConnected() != connected) {
                    camera.setConnected(connected);
                    cameraRepository.save(camera);
                    log.info("카메라 연결 상태 변경: {} -> {}", camera.getName(), connected);
                    hasChanges = true;
                }
            }

            log.info("카메라 동기화 완료: MediaMTX={}, DB={}", mtxCameras.size(), dbCameras.size());

            // 변경 시 SSE 알림 및 Redis에 분석 목록 동기화
            if (hasChanges) {
                sseEmitterService.broadcastCamera("refresh");
                cameraService.syncAnalysisCamerasToRedis();
                log.info("카메라 목록 갱신 SSE 및 Redis 동기화 완료");
            }

        } catch (Exception e) {
            log.error("카메라 동기화 실패: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchCamerasFromMediaMTX() {
        try {
            WebClient client = webClientBuilder.baseUrl(mediaMtxApiUrl).build();
            Map<String, Object> response = client.get()
                    .uri("/v3/paths/list")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                return items.stream()
                        .map(item -> (String) item.get("name"))
                        .filter(name -> name != null && !name.isEmpty())
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.error("MediaMTX API 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
