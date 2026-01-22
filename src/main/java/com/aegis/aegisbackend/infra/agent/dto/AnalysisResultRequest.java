package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

@Data
public class AnalysisResultRequest {
    private String agentAction;      // 권장 조치
    private String summary;          // 요약
    private String analysisReport;   // 상세 분석 리포트
}
