package com.aegis.aegisbackend.infra.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Agent 서비스
 * - Agent 백엔드(Python) 상태 확인
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final WebClient.Builder webClientBuilder;

    @Value("${agent.api-url:http://localhost:8001}")
    private String agentApiUrl;

    @Value("${agent.enabled:false}")
    private boolean agentEnabled;

    @Value("${agent.timeout-seconds:30}")
    private int timeoutSeconds;

    /**
     * Agent 서버 상태 확인
     */
    public boolean isAgentServerHealthy() {
        if (!agentEnabled) {
            return false;
        }

        try {
            String response = webClientBuilder.build()
                    .get()
                    .uri(agentApiUrl + "/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            return response != null;
        } catch (Exception e) {
            log.warn("Agent 서버 헬스체크 실패 - url={}, error={}", agentApiUrl, e.getMessage());
            return false;
        }
    }
}
