package com.aegis.aegisbackend.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Agent 분석 응답 DTO
 * - 영상 프레임 분석 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalysisResponse {

    /** 카메라 ID */
    private UUID cameraId;

    /** 위험 상황 여부 */
    private boolean isDangerous;

    /** 신뢰도 (0.0 ~ 1.0) */
    private double confidence;

    /** 감지된 이벤트 타입 (ASSAULT, THEFT, SUSPICIOUS, NORMAL) */
    private String eventType;

    /** 상황 설명 */
    private String description;

    /** AI 권장 조치 */
    private String recommendedAction;

    /** 분석 요약 */
    private String summary;

    /** 상세 분석 리포트 */
    private String analysisReport;
}
