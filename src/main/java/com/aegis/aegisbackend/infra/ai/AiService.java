package com.aegis.aegisbackend.infra.ai;

import com.aegis.aegisbackend.infra.ai.dto.AiAnalysisRequest;
import com.aegis.aegisbackend.infra.ai.dto.AiAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * AI 서비스
 * - 8프레임 버퍼를 AI 백엔드(Python)에 전송하여 분석
 * - 분석 결과를 받아서 반환
 *
 * AI 백엔드에서 처리하는 기능:
 * - 영상 프레임 분석 (위험 상황 감지)
 * - 자동 대응 조치 (TODO: AI 백엔드에서 구현 예정)
 * - 비상연락처 알림 (TODO: AI 백엔드에서 구현 예정)
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

    @Value("${ai.timeout-seconds:30}")
    private int timeoutSeconds;

    /**
     * 프레임 버퍼 분석 요청
     * - 8장의 프레임을 AI 백엔드에 전송하여 분석
     *
     * @param cameraId 카메라 ID
     * @param frames Base64 인코딩된 프레임 이미지 목록 (8장)
     * @return 분석 결과
     */
    public AiAnalysisResponse analyzeFrames(UUID cameraId, List<String> frames) {
        if (!aiEnabled) {
            log.debug("AI 비활성화 상태 - cameraId={}", cameraId);
            return AiAnalysisResponse.builder()
                    .cameraId(cameraId)
                    .isDangerous(false)
                    .confidence(0.0)
                    .eventType(null)
                    .description("AI 비활성화 상태")
                    .build();
        }

        log.info("AI 분석 요청 - cameraId={}, frames={}", cameraId, frames.size());

        try {
            AiAnalysisRequest request = AiAnalysisRequest.builder()
                    .cameraId(cameraId)
                    .frames(frames)
                    .build();

            AiAnalysisResponse response = webClientBuilder.build()
                    .post()
                    .uri(aiApiUrl + "/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiAnalysisResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response != null) {
                log.info("AI 분석 완료 - cameraId={}, isDangerous={}, eventType={}",
                        cameraId, response.isDangerous(), response.getEventType());
                return response;
            }

            return createErrorResponse(cameraId, "AI 응답 없음");

        } catch (Exception e) {
            log.error("AI 분석 실패 - cameraId={}, error={}", cameraId, e.getMessage());
            return createErrorResponse(cameraId, "AI 분석 실패: " + e.getMessage());
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

    private AiAnalysisResponse createErrorResponse(UUID cameraId, String message) {
        return AiAnalysisResponse.builder()
                .cameraId(cameraId)
                .isDangerous(false)
                .confidence(0.0)
                .eventType(null)
                .description(message)
                .build();
    }
}
