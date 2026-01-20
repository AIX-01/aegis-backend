package com.aegis.aegisbackend.domain.stream.service;

import com.aegis.aegisbackend.infra.ai.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 프레임 버퍼 서비스
 * - 썸네일: Redis 저장 (카메라당 최신 1장, 5초 TTL)
 * - AI 버퍼: 메모리 저장 (카메라당 8장 수집 후 AI 백엔드에 전송)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrameBufferService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AiService aiService;

    private static final String THUMBNAIL_PREFIX = "thumbnail:";
    private static final int THUMBNAIL_TTL_SECONDS = 5;
    private static final int AI_BUFFER_SIZE = 8;

    private final Map<UUID, LinkedList<byte[]>> aiBuffers = new ConcurrentHashMap<>();

    /** 프레임 수신 처리: 썸네일 저장 + AI 버퍼 추가 */
    public void processFrame(UUID cameraId, byte[] frameData) {
        saveThumbnail(cameraId, frameData);
        List<byte[]> fullBuffer = addToAiBuffer(cameraId, frameData);

        // 버퍼가 가득 차면 AI 백엔드에 전송
        if (fullBuffer != null) {
            sendToAi(cameraId, fullBuffer);
        }
    }

    /** Redis에서 썸네일 조회 (Base64) */
    public String getThumbnail(UUID cameraId) {
        return redisTemplate.opsForValue().get(THUMBNAIL_PREFIX + cameraId);
    }

    /** 특정 카메라 AI 버퍼 초기화 */
    public void clearAiBuffer(UUID cameraId) {
        aiBuffers.remove(cameraId);
    }

    /** 모든 AI 버퍼 초기화 */
    public void clearAllAiBuffers() {
        aiBuffers.clear();
    }

    /** AI 버퍼 상태 조회 (디버깅용) */
    public Map<UUID, Integer> getAiBufferStatus() {
        Map<UUID, Integer> status = new HashMap<>();
        aiBuffers.forEach((id, buffer) -> {
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

    private List<byte[]> addToAiBuffer(UUID cameraId, byte[] frameData) {
        LinkedList<byte[]> buffer = aiBuffers.computeIfAbsent(cameraId, k -> new LinkedList<>());
        synchronized (buffer) {
            buffer.add(frameData);
            if (buffer.size() >= AI_BUFFER_SIZE) {
                List<byte[]> frames = new ArrayList<>(buffer);
                buffer.clear();
                log.info("AI 버퍼 가득 참: cameraId={}, frames={}", cameraId, frames.size());
                return frames;
            }
        }
        return null;
    }

    /**
     * AI 백엔드에 프레임 전송 (fire-and-forget)
     */
    private void sendToAi(UUID cameraId, List<byte[]> frames) {
        List<String> base64Frames = frames.stream()
                .map(Base64.getEncoder()::encodeToString)
                .collect(Collectors.toList());

        aiService.sendFramesAsync(cameraId, base64Frames);
    }
}
