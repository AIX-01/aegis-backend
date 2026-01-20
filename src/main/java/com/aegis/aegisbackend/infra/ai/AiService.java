package com.aegis.aegisbackend.infra.ai;

import com.aegis.aegisbackend.infra.ai.dto.AiAnalysisRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * AI 서비스
 * - 8프레임 버퍼를 AI 백엔드(Python)에 비동기 전송
 * - 응답을 기다리지 않음 (fire-and-forget)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final WebClient.Builder webClientBuilder;

    @Value("${ai.api-url:http://localhost:8001}")
    private String aiApiUrl;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    /**
     * 프레임 버퍼를 AI 백엔드에 비동기 전송 (fire-and-forget)
     * - 8장의 프레임을 AI 백엔드에 전송
     * - 응답을 기다리지 않음
     *
     * @param cameraId 카메라 ID
     * @param frames Base64 인코딩된 프레임 이미지 목록 (8장)
     */
    @Async
    public void sendFramesAsync(UUID cameraId, List<String> frames) {
        if (!aiEnabled) {
            log.debug("AI 비활성화 상태 - cameraId={}", cameraId);
            return;
        }

        log.info("AI 분석 요청 전송 - cameraId={}, frames={}", cameraId, frames.size());

        try {
            AiAnalysisRequest request = AiAnalysisRequest.builder()
                    .cameraId(cameraId)
                    .frames(frames)
                    .build();

            webClientBuilder.build()
                    .post()
                    .uri(aiApiUrl + "/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .subscribe(
                            response -> log.debug("AI 응답 수신: cameraId={}", cameraId),
                            error -> log.warn("AI 요청 실패: cameraId={}, error={}", cameraId, error.getMessage())
                    );

        } catch (Exception e) {
            log.error("AI 요청 전송 실패 - cameraId={}, error={}", cameraId, e.getMessage());
        }
    }

    /**
     * AI 서버 상태 확인
     */
    public boolean isAiServerHealthy() {
        if (!aiEnabled) {
            return false;
        }

        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(aiApiUrl + "/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            return response != null;
        } catch (Exception e) {
            log.warn("AI 서버 헬스체크 실패 - url={}, error={}", aiApiUrl, e.getMessage());
            return false;
        }
    }
}
