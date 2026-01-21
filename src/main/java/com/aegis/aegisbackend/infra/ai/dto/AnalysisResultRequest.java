package com.aegis.aegisbackend.infra.ai.dto;

import lombok.Data;

@Data
public class AnalysisResultRequest {
    private String aiAction;         // 권장 조치
    private String summary;          // 요약
    private String analysisReport;   // 상세 분석 리포트
}
