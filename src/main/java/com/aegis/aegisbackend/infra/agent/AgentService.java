package com.aegis.aegisbackend.infra.agent;

import com.aegis.aegisbackend.infra.agent.dto.AgentActionResponse;
import com.aegis.aegisbackend.infra.agent.dto.AgentAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Agent 서비스
 * - 영상 프레임 분석 (8프레임 버퍼)
 * - 위험 상황 감지 시 자동 대응 조치 수행
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
     * 프레임 버퍼 분석 요청
     * - 8장의 프레임을 Agent에 전송하여 분석
     *
     * @param cameraId 카메라 ID
     * @param frames Base64 인코딩된 프레임 이미지 목록 (8장)
     * @return 분석 결과
     */
    public AgentAnalysisResponse analyzeFrames(UUID cameraId, List<String> frames) {
        if (!agentEnabled) {
            log.debug("Agent 비활성화 상태 - cameraId={}", cameraId);
            return AgentAnalysisResponse.builder()
                    .cameraId(cameraId)
                    .isDangerous(false)
                    .confidence(0.0)
                    .eventType(null)
                    .description("Agent 비활성화 상태")
                    .build();
        }

        log.info("Agent 분석 요청 - cameraId={}, frames={}", cameraId, frames.size());

        // TODO: 실제 Agent API 호출 구현
        // WebClient를 사용하여 Agent 서버에 요청
        // 현재는 스텁 응답 반환

        return AgentAnalysisResponse.builder()
                .cameraId(cameraId)
                .isDangerous(false)
                .confidence(0.0)
                .eventType(null)
                .description("Agent 분석 미구현 - 스텁 응답")
                .build();
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

    /**
     * 위험 상황 감지 시 Agent에 조치 요청
     *
     * @param cameraId 카메라 ID
     * @param eventId 이벤트 ID
     * @param analysisResult Agent 분석 결과
     * @return Agent 조치 결과
     */
    public AgentActionResponse requestAction(UUID cameraId, UUID eventId, AgentAnalysisResponse analysisResult) {
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
        // 1. 비상연락처 알림 발송
        // 2. 이벤트 상태 업데이트

        return AgentActionResponse.builder()
                .eventId(eventId)
                .actionTaken(false)
                .actionType("NONE")
                .message("Agent 미구현 - 스텁 응답")
                .build();
    }

    /**
     * 비상연락처 알림 발송
     *
     * @param eventId 이벤트 ID
     * @param message 알림 메시지
     */
    public void sendEmergencyNotification(UUID eventId, String message) {
        log.info("비상 알림 발송 요청 - eventId={}, message={}", eventId, message);

        // TODO: 비상연락처 조회 및 알림 발송 구현
    }
}
