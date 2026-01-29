package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

/**
 * 이벤트 생성 요청 DTO (Agent → Spring)
 */
@Data
public class CreateEventRequest {
    private String cameraId;
    private String risk;
    private String type;
    private String occurredAt;
}
