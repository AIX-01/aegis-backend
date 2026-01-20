package com.aegis.aegisbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 프레임 버퍼 서비스
 * - 썸네일: Redis에 카메라당 최신 1장 저장 (브라우저 조회용)
 * - VLM 버퍼: Spring 메모리에 8장 수집 (VLM 분석용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrameBufferService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String THUMBNAIL_PREFIX = "thumbnail:";
    private static final int THUMBNAIL_TTL_SECONDS = 5;
    private static final int VLM_BUFFER_SIZE = 8;

    // VLM 분석용 프레임 버퍼 (카메라별 8장 수집)
    private final Map<UUID, LinkedList<byte[]>> vlmBuffers = new ConcurrentHashMap<>();

    /**
     * 프레임 수신 처리
     * 1. Redis에 썸네일 저장 (최신 1장)
     * 2. VLM 버퍼에 추가 (8장 수집)
     *
     * @return 8장이 모이면 해당 프레임 리스트 반환, 아니면 null
     */
    public List<byte[]> processFrame(UUID cameraId, byte[] frameData) {
        // 1. 썸네일 저장 (Redis, Base64)
        saveThumbnail(cameraId, frameData);

        // 2. VLM 버퍼에 추가
        return addToVlmBuffer(cameraId, frameData);
    }

    /**
     * Redis에 썸네일 저장 (Base64 인코딩)
     */
    private void saveThumbnail(UUID cameraId, byte[] frameData) {
        String key = THUMBNAIL_PREFIX + cameraId.toString();
        String base64Image = Base64.getEncoder().encodeToString(frameData);
        redisTemplate.opsForValue().set(key, base64Image, THUMBNAIL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Redis에서 썸네일 조회
     * @return Base64 인코딩된 이미지, 없으면 null
     */
    public String getThumbnail(UUID cameraId) {
        String key = THUMBNAIL_PREFIX + cameraId.toString();
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * VLM 버퍼에 프레임 추가
     * @return 8장이 모이면 프레임 리스트 반환 후 버퍼 비움, 아니면 null
     */
    private List<byte[]> addToVlmBuffer(UUID cameraId, byte[] frameData) {
        LinkedList<byte[]> buffer = vlmBuffers.computeIfAbsent(cameraId, k -> new LinkedList<>());

        synchronized (buffer) {
            buffer.add(frameData);

            if (buffer.size() >= VLM_BUFFER_SIZE) {
                // 8장 모임 - 복사 후 버퍼 비움
                List<byte[]> frames = new ArrayList<>(buffer);
                buffer.clear();
                log.info("VLM buffer full for camera {}: {} frames collected", cameraId, frames.size());
                return frames;
            }
        }

        return null;
    }

    /**
     * 특정 카메라의 VLM 버퍼 비우기 (카메라 연결 해제 시 등)
     */
    public void clearVlmBuffer(UUID cameraId) {
        LinkedList<byte[]> buffer = vlmBuffers.remove(cameraId);
        if (buffer != null) {
            log.debug("VLM buffer cleared for camera {}", cameraId);
        }
    }

    /**
     * 모든 VLM 버퍼 비우기
     */
    public void clearAllVlmBuffers() {
        vlmBuffers.clear();
        log.info("All VLM buffers cleared");
    }

    /**
     * 현재 VLM 버퍼 상태 조회 (디버깅용)
     */
    public Map<UUID, Integer> getVlmBufferStatus() {
        Map<UUID, Integer> status = new HashMap<>();
        vlmBuffers.forEach((cameraId, buffer) -> {
            synchronized (buffer) {
                status.put(cameraId, buffer.size());
            }
        });
        return status;
    }
}
