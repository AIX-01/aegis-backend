package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 분석 결과 요청 DTO (Agent → Spring)
 */
@Data
public class AnalysisResultRequest {
    private String summary;
    private String riskScore;
    private List<ActionRequest> actions;
    private List<Map<String, Object>> ragReferences;
    private String report;

    @Data
    public static class ActionRequest {
        private String log;
        private String triggeredAt;  // ISO8601
    }
}
