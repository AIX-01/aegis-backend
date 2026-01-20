package com.aegis.aegisbackend.domain.stream.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 프레임 버퍼 서비스
 * - 썸네일: Redis 저장 (카메라당 최신 1장, 5초 TTL)
 * - VLM 버퍼: 메모리 저장 (카메라당 8장 수집 후 분석)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrameBufferService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String THUMBNAIL_PREFIX = "thumbnail:";
    private static final int THUMBNAIL_TTL_SECONDS = 5;
    private static final int VLM_BUFFER_SIZE = 8;

    private final Map<UUID, LinkedList<byte[]>> vlmBuffers = new ConcurrentHashMap<>();

    /** 프레임 수신 처리: 썸네일 저장 + VLM 버퍼 추가 */
    public List<byte[]> processFrame(UUID cameraId, byte[] frameData) {
        saveThumbnail(cameraId, frameData);
        return addToVlmBuffer(cameraId, frameData);
    }

    /** Redis에서 썸네일 조회 (Base64) */
    public String getThumbnail(UUID cameraId) {
        return redisTemplate.opsForValue().get(THUMBNAIL_PREFIX + cameraId);
    }

    /** 특정 카메라 VLM 버퍼 초기화 */
    public void clearVlmBuffer(UUID cameraId) {
        vlmBuffers.remove(cameraId);
    }

    /** 모든 VLM 버퍼 초기화 */
    public void clearAllVlmBuffers() {
        vlmBuffers.clear();
    }

    /** VLM 버퍼 상태 조회 (디버깅용) */
    public Map<UUID, Integer> getVlmBufferStatus() {
        Map<UUID, Integer> status = new HashMap<>();
        vlmBuffers.forEach((id, buffer) -> {
            synchronized (buffer) {
                status.put(id, buffer.size());
            }
        });
        return status;
    }

    // === Private ===

    private void saveThumbnail(UUID cameraId, byte[] frameData) {
        String key = THUMBNAIL_PREFIX + cameraId;
        String base64 = Base64.getEncoder().encodeToString(frameData);
        redisTemplate.opsForValue().set(key, base64, THUMBNAIL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private List<byte[]> addToVlmBuffer(UUID cameraId, byte[] frameData) {
        LinkedList<byte[]> buffer = vlmBuffers.computeIfAbsent(cameraId, k -> new LinkedList<>());
        synchronized (buffer) {
            buffer.add(frameData);
            if (buffer.size() >= VLM_BUFFER_SIZE) {
                List<byte[]> frames = new ArrayList<>(buffer);
                buffer.clear();
                log.info("VLM 버퍼 가득 참: cameraId={}, frames={}", cameraId, frames.size());
                return frames;
            }
        }
        return null;
    }
}
