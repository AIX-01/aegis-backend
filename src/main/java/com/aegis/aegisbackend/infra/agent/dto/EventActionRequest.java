package com.aegis.aegisbackend.infra.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 이벤트 액션 생성 요청 DTO (Agent → Spring)
 */
@Data
public class EventActionRequest {

    @NotBlank(message = "action은 필수입니다")
    private String action;

    @NotBlank(message = "description은 필수입니다")
    private String description;
}

