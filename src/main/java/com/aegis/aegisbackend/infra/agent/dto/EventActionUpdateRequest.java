package com.aegis.aegisbackend.infra.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 이벤트 액션 수정 요청 DTO (Agent → Spring)
 */
@Data
public class EventActionUpdateRequest {
    private String userId;

    @NotBlank(message = "action은 필수입니다")
    private String action;

    @NotBlank(message = "description은 필수입니다")
    private String description;
}

