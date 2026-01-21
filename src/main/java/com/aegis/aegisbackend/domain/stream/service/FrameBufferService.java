package com.aegis.aegisbackend.domain.stream.service;

import com.aegis.aegisbackend.infra.ai.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 프레임 버퍼 서비스
 * - 썸네일: Redis 저장 (카메라당 최신 1장, 5초 TTL)
 * - AI 버퍼: 메모리 저장 (카메라당 8장 수집 후 AI 백엔드에 전송)
 * - 타임아웃: 3초 이상 프레임이 안 들어오면 버퍼 폐기 (8장 미만은 전송 안함)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrameBufferService {

    private final RedisTemplate<String, String> redisTemplate;
    private final AiService aiService;

    private static final String THUMBNAIL_PREFIX = "thumbnail:";
    private static final int THUMBNAIL_TTL_SECONDS = 3;
    private static final int AI_BUFFER_SIZE = 8;
    private static final long BUFFER_TIMEOUT_MS = 3_000;  // 3초 타임아웃

    // 카메라별 AI 버퍼
    private final Map<UUID, LinkedList<byte[]>> aiBuffers = new ConcurrentHashMap<>();
    // 카메라별 마지막 프레임 수신 시간
    private final Map<UUID, Long> lastFrameTime = new ConcurrentHashMap<>();

    /** 프레임 수신 처리: 썸네일 저장 + AI 버퍼 추가 */
    public void processFrame(UUID cameraId, byte[] frameData) {
        saveThumbnail(cameraId, frameData);
        lastFrameTime.put(cameraId, System.currentTimeMillis());
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
        lastFrameTime.remove(cameraId);
    }

    /** 모든 AI 버퍼 초기화 */
    public void clearAllAiBuffers() {
        aiBuffers.clear();
        lastFrameTime.clear();
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

    /**
     * 타임아웃된 버퍼 처리 (1초마다 실행)
     * - 3초 이상 프레임이 안 들어온 버퍼는 폐기 (8장 미만은 전송하지 않음)
     */
    @Scheduled(fixedRate = 1000)
    public void flushTimedOutBuffers() {
        long now = System.currentTimeMillis();

        List<UUID> camerasToDiscard = new ArrayList<>();

        lastFrameTime.forEach((cameraId, lastTime) -> {
            if (now - lastTime > BUFFER_TIMEOUT_MS) {
                LinkedList<byte[]> buffer = aiBuffers.get(cameraId);
                if (buffer != null && !buffer.isEmpty()) {
                    camerasToDiscard.add(cameraId);
                }
            }
        });

        // 타임아웃된 버퍼 폐기
        for (UUID cameraId : camerasToDiscard) {
            LinkedList<byte[]> buffer = aiBuffers.get(cameraId);
            if (buffer != null) {
                int discardedCount;
                synchronized (buffer) {
                    discardedCount = buffer.size();
                    buffer.clear();
                }
                lastFrameTime.remove(cameraId);
                log.debug("버퍼 타임아웃 폐기: cameraId={}, frames={}", cameraId, discardedCount);
            }
        }
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
