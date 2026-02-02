package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

/**
 * 분석 결과 요청 DTO (Agent → Spring)
 * AI Agent가 정밀 분석 후 이벤트 업데이트 시 사용
 */
@Data
public class AnalysisResultRequest {
    private String risk;       // NORMAL, SUSPICIOUS, ABNORMAL
    private String type;       // ASSAULT, BURGLARY, DUMP, SWOON, VANDALISM
    private String summary;    // AI 분석 요약
    private String riskScore;  // 위험 점수
}
