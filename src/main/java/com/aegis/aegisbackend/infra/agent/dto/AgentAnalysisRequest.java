package com.aegis.aegisbackend.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Agent 분석 요청 DTO
 * - 영상 프레임 분석 요청
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAnalysisRequest {

    /** 카메라 ID */
    private UUID cameraId;

    /** Base64 인코딩된 프레임 이미지 목록 */
    private List<String> frames;
}
