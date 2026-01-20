package com.aegis.aegisbackend.infra.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * AI 분석 요청 DTO
 * - 8프레임 버퍼를 AI 백엔드에 전송
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRequest {

    /** 카메라 ID */
    private UUID cameraId;

    /** Base64 인코딩된 프레임 이미지 목록 (8장) */
    private List<String> frames;
}
