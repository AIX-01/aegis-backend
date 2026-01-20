package com.aegis.aegisbackend.infra.vlm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * VLM 분석 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VlmAnalysisRequest {

    /** 카메라 ID */
    private UUID cameraId;

    /** Base64 인코딩된 프레임 이미지 목록 */
    private List<String> frames;

    /** 분석 타임스탬프 */
    private String timestamp;
}
