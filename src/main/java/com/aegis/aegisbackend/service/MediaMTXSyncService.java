package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.entity.Camera;
import com.aegis.aegisbackend.repository.CameraRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMTXSyncService {

    private final CameraRepository cameraRepository;
    private final RedisTokenService redisTokenService;
    private final WebClient.Builder webClientBuilder;

    @Value("${mediamtx.api-url}")
    private String mediaMtxApiUrl;

    /**
     * MediaMTX Webhook 수신 시 호출
     * 1초 플래그로 중복 호출 방지
     */
    @Async
    public void onWebhookReceived() {
        // 1초 플래그 체크 - 이미 잠금 상태면 무시
        if (redisTokenService.isSyncLocked()) {
            log.debug("Sync already scheduled, skipping...");
            return;
        }

        // 1초 플래그 설정
        if (!redisTokenService.tryAcquireSyncLock()) {
            log.debug("Failed to acquire sync lock, another process is handling it");
            return;
        }

        // 1초 후 동기화 실행 (플래그 만료 후)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        syncCameras();
    }

    /**
     * MediaMTX에서 카메라 목록을 가져와 DB와 동기화
     */
    @Transactional
    public void syncCameras() {
        log.info("Starting camera sync with MediaMTX...");

        try {
            List<String> mediaMtxCameras = fetchCamerasFromMediaMTX();
            Set<String> mediaMtxCameraSet = Set.copyOf(mediaMtxCameras);

            // DB에 있는 모든 카메라 조회
            List<Camera> dbCameras = cameraRepository.findAll();
            Set<String> dbCameraNames = dbCameras.stream()
                    .map(Camera::getName)
                    .collect(Collectors.toSet());

            // 1. MediaMTX에 있지만 DB에 없는 카메라 -> 새로 추가 (active=false)
            for (String cameraName : mediaMtxCameras) {
                if (!dbCameraNames.contains(cameraName)) {
                    Camera newCamera = Camera.builder()
                            .name(cameraName)
                            .alias(cameraName)
                            .connected(true)
                            .active(false) // 새 카메라는 비활성화 상태로 추가
                            .build();
                    cameraRepository.save(newCamera);
                    log.info("New camera added: {} (active=false)", cameraName);
                }
            }

            // 2. DB 카메라들의 connected 상태 업데이트
            for (Camera camera : dbCameras) {
                boolean shouldBeConnected = mediaMtxCameraSet.contains(camera.getName());
                if (camera.getConnected() != shouldBeConnected) {
                    camera.setConnected(shouldBeConnected);
                    cameraRepository.save(camera);
                    log.info("Camera {} connected status updated to: {}", camera.getName(), shouldBeConnected);
                }
            }

            log.info("Camera sync completed. MediaMTX cameras: {}, DB cameras: {}",
                    mediaMtxCameras.size(), dbCameras.size());

        } catch (Exception e) {
            log.error("Failed to sync cameras with MediaMTX: {}", e.getMessage(), e);
        }
    }

    /**
     * MediaMTX API에서 현재 연결된 카메라 목록 조회
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchCamerasFromMediaMTX() {
        try {
            WebClient webClient = webClientBuilder.baseUrl(mediaMtxApiUrl).build();

            Map<String, Object> response = webClient.get()
                    .uri("/v3/paths/list")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                return items.stream()
                        .map(item -> (String) item.get("name"))
                        .filter(name -> name != null && !name.isEmpty())
                        .collect(Collectors.toList());
            }

            return List.of();
        } catch (Exception e) {
            log.error("Failed to fetch cameras from MediaMTX: {}", e.getMessage());
            return List.of();
        }
    }
}

