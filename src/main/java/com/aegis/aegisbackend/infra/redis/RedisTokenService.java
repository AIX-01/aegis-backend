package com.aegis.aegisbackend.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 토큰 관리 서비스
 * - Refresh Token: 7일 TTL
 * - MediaMTX 동기화 잠금: 1초 TTL
 * - Camera Analysis: 분석 대상 카메라 목록 저장 + Pub/Sub 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String SYNC_LOCK_KEY = "mediamtx:sync:lock";
    private static final String ANALYSIS_CAMERAS_KEY = "analysis:cameras";
    private static final String CAMERA_ANALYSIS_CHANNEL = "camera:analysis:update";

    // === Refresh Token ===

    public void saveRefreshToken(String refreshToken, UUID userId, long expirationMs) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, userId.toString(), expirationMs, TimeUnit.MILLISECONDS);
    }

    public String getUserIdByRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.delete(key);
    }


    // === MediaMTX 동기화 잠금 ===

    public boolean tryAcquireSyncLock() {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(SYNC_LOCK_KEY, "locked", 1, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public boolean isSyncLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SYNC_LOCK_KEY));
    }

    // === Camera Analysis ===

    /**
     * 분석 대상 카메라 목록 저장 및 Pub/Sub 알림 발행
     * @param cameras [{id: "uuid", name: "cam1", location: "1층 로비"}, ...]
     */
    public void saveAnalysisCamerasAndNotify(List<Map<String, String>> cameras) {
        try {
            String json = objectMapper.writeValueAsString(cameras);
            redisTemplate.opsForValue().set(ANALYSIS_CAMERAS_KEY, json);
            redisTemplate.convertAndSend(CAMERA_ANALYSIS_CHANNEL, "sync");
            log.info("분석 카메라 목록 저장 및 알림 발행: {} 대", cameras.size());
        } catch (JsonProcessingException e) {
            log.error("분석 카메라 목록 JSON 변환 실패", e);
        }
    }

    /**
     * 카메라 분석 상태 변경 알림 발행 (목록 변경 없이 알림만)
     */
    public void publishCameraAnalysisUpdate() {
        redisTemplate.convertAndSend(CAMERA_ANALYSIS_CHANNEL, "sync");
        log.info("카메라 분석 상태 변경 알림 발행: channel={}", CAMERA_ANALYSIS_CHANNEL);
    }
}
