package com.aegis.aegisbackend.infra.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 이벤트 생성 요청 DTO (Agent → Spring)
 */
@Data
public class CreateEventRequest {
    @NotBlank(message = "cameraId는 필수입니다")
    private String cameraId;

    @NotBlank(message = "risk는 필수입니다")
    private String risk;

    @NotBlank(message = "type은 필수입니다")
    private String type;

    private String occurredAt;
}
