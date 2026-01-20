package com.aegis.aegisbackend.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Agent 조치 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionRequest {

    /** 카메라 ID */
    private UUID cameraId;

    /** 이벤트 ID */
    private UUID eventId;

    /** 이벤트 타입 */
    private String eventType;

    /** 신뢰도 */
    private double confidence;

    /** 상황 설명 */
    private String description;

    /** 권장 조치 */
    private String recommendedAction;

    /** 클립 추출 여부 */
    private boolean extractClip;

    /** 비상 알림 발송 여부 */
    private boolean sendEmergencyAlert;
}
