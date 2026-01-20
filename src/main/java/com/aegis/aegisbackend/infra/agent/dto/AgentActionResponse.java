package com.aegis.aegisbackend.infra.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Agent 조치 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionResponse {

    /** 이벤트 ID */
    private UUID eventId;

    /** 조치 수행 여부 */
    private boolean actionTaken;

    /** 조치 유형 (CLIP_SAVED, ALERT_SENT, ESCALATED 등) */
    private String actionType;

    /** 저장된 클립 URL */
    private String clipUrl;

    /** 조치 결과 메시지 */
    private String message;

    /** 알림 발송 대상 수 */
    private int notifiedCount;
}
