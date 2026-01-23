package com.aegis.aegisbackend.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 토큰 관리 서비스
 * - Refresh Token: 7일 TTL
 * - Stream Token: 30초 TTL (일회용)
 * - MediaMTX 동기화 잠금: 1초 TTL
 * - Camera Analysis Pub/Sub: 카메라 분석 상태 변경 알림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String STREAM_TOKEN_PREFIX = "stream_token:";
    private static final String SYNC_LOCK_KEY = "mediamtx:sync:lock";
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

    // === Stream Token (일회용) ===

    /** 스트림 토큰 생성 (userId:cameraId 형태로 저장) */
    public String generateStreamToken(UUID userId, UUID cameraId) {
        String token = UUID.randomUUID().toString();
        String key = STREAM_TOKEN_PREFIX + token;
        String value = userId + ":" + cameraId;
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.SECONDS);
        return token;
    }

    /** 스트림 토큰 검증 후 삭제 (일회용) */
    public String validateAndConsumeStreamToken(String token) {
        String key = STREAM_TOKEN_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.delete(key);
            String[] parts = value.split(":");
            if (parts.length == 2) {
                return parts[1];
            }
        }
        return null;
    }

    /** 스트림 토큰 유효성만 확인 */
    public boolean isStreamTokenValid(String token) {
        String key = STREAM_TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // === MediaMTX 동기화 잠금 ===

    public boolean tryAcquireSyncLock() {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(SYNC_LOCK_KEY, "locked", 1, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public boolean isSyncLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SYNC_LOCK_KEY));
    }

    // === Camera Analysis Pub/Sub ===

    /**
     * 카메라 분석 상태 변경 알림 발행
     * Python Agent가 이 채널을 구독하여 분석 대상 카메라 목록을 갱신
     */
    public void publishCameraAnalysisUpdate() {
        redisTemplate.convertAndSend(CAMERA_ANALYSIS_CHANNEL, "update");
        log.info("카메라 분석 상태 변경 알림 발행: channel={}", CAMERA_ANALYSIS_CHANNEL);
    }
}
