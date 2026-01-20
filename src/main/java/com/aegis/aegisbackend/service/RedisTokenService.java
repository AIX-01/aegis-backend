package com.aegis.aegisbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String SYNC_LOCK_KEY = "mediamtx:sync:lock";
    private static final String STREAM_TOKEN_PREFIX = "stream_token:";

    // Refresh Token 관리 (7일 TTL)
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

    // MediaMTX 동기화 잠금 (1초 TTL)
    public boolean tryAcquireSyncLock() {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(SYNC_LOCK_KEY, "locked", 1, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public boolean isSyncLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(SYNC_LOCK_KEY));
    }

    // Stream Token 관리 (일회용, 30초 TTL)
    public String generateStreamToken(UUID userId, UUID cameraId) {
        String token = UUID.randomUUID().toString();
        String key = STREAM_TOKEN_PREFIX + token;
        // 값: userId:cameraId 형태로 저장
        String value = userId.toString() + ":" + cameraId.toString();
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 스트림 토큰 검증 및 소비 (일회용 - 검증 후 삭제)
     * @return cameraId if valid, null otherwise
     */
    public String validateAndConsumeStreamToken(String token) {
        String key = STREAM_TOKEN_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            // 일회용이므로 즉시 삭제
            redisTemplate.delete(key);
            // cameraId 부분만 반환
            String[] parts = value.split(":");
            if (parts.length == 2) {
                return parts[1]; // cameraId
            }
        }
        return null;
    }

    /**
     * 스트림 토큰 유효성 확인 (삭제하지 않음)
     */
    public boolean isStreamTokenValid(String token) {
        String key = STREAM_TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
