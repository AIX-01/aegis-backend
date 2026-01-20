package com.aegis.aegisbackend.infra.agent;

import com.aegis.aegisbackend.infra.agent.dto.AgentActionRequest;
import com.aegis.aegisbackend.infra.agent.dto.AgentActionResponse;
import com.aegis.aegisbackend.infra.vlm.dto.VlmAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent 서비스
 * - VLM 분석 결과를 기반으로 자동 대응 조치 수행
 * - 비상연락처 알림, 클립 저장 등 처리
 *
 * TODO: 실제 Agent API 연동 구현 필요
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    @Value("${agent.api-url:http://localhost:8001}")
    private String agentApiUrl;

    @Value("${agent.enabled:false}")
    private boolean agentEnabled;

    /**
     * 위험 상황 감지 시 Agent에 조치 요청
     *
     * @param cameraId 카메라 ID
     * @param eventId 이벤트 ID
     * @param analysisResult VLM 분석 결과
     * @return Agent 조치 결과
     */
    public AgentActionResponse requestAction(UUID cameraId, UUID eventId, VlmAnalysisResponse analysisResult) {
        if (!agentEnabled) {
            log.debug("Agent 비활성화 상태 - cameraId={}, eventId={}", cameraId, eventId);
            return AgentActionResponse.builder()
                    .eventId(eventId)
                    .actionTaken(false)
                    .actionType("NONE")
                    .message("Agent 비활성화 상태")
                    .build();
        }

        log.info("Agent 조치 요청 - cameraId={}, eventId={}, eventType={}",
                cameraId, eventId, analysisResult.getEventType());

        // TODO: 실제 Agent API 호출 구현
        // 1. 클립 추출 요청
        // 2. MinIO에 클립 저장
        // 3. 비상연락처 알림 발송
        // 4. 이벤트 상태 업데이트

        return AgentActionResponse.builder()
                .eventId(eventId)
                .actionTaken(false)
                .actionType("NONE")
                .message("Agent 미구현 - 스텁 응답")
                .build();
    }

    /**
     * 클립 추출 및 저장 요청
     *
     * @param cameraId 카메라 ID
     * @param eventId 이벤트 ID
     * @param durationSeconds 클립 길이 (초)
     * @return 저장된 클립 URL
     */
    public String extractAndSaveClip(UUID cameraId, UUID eventId, int durationSeconds) {
        log.info("클립 추출 요청 - cameraId={}, eventId={}, duration={}s",
                cameraId, eventId, durationSeconds);

        // TODO: FFmpeg를 통한 클립 추출 및 MinIO 저장 구현
        return null;
    }

    /**
     * 비상연락처 알림 발송
     *
     * @param eventId 이벤트 ID
     * @param message 알림 메시지
     */
    public void sendEmergencyNotification(UUID eventId, String message) {
        log.info("비상 알림 발송 요청 - eventId={}", eventId);

        // TODO: 비상연락처 조회 및 알림 발송 구현
    }

    /**
     * Agent 서버 상태 확인
     */
    public boolean isAgentServerHealthy() {
        if (!agentEnabled) {
            return false;
        }

        // TODO: Agent 서버 헬스체크 구현
        log.debug("Agent 서버 상태 확인 - url={}", agentApiUrl);
        return false;
    }
}
