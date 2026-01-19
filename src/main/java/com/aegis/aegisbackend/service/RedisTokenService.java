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

    private static final String ACCESS_TOKEN_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String SYNC_LOCK_KEY = "mediamtx:sync:lock";

    // Access Token 관리 (15분 TTL)
    public void saveAccessToken(UUID userId, String accessToken, long expirationMs) {
        String key = ACCESS_TOKEN_PREFIX + userId.toString();
        redisTemplate.opsForValue().set(key, accessToken, expirationMs, TimeUnit.MILLISECONDS);
    }

    public String getAccessToken(UUID userId) {
        String key = ACCESS_TOKEN_PREFIX + userId.toString();
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteAccessToken(UUID userId) {
        String key = ACCESS_TOKEN_PREFIX + userId.toString();
        redisTemplate.delete(key);
    }

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
}

