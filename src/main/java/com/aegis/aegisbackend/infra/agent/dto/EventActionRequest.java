package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

/**
 * 이벤트 액션 생성/수정 요청 DTO (Agent → Spring)
 */
@Data
public class EventActionRequest {
    private String userId;
    private String action;
    private String description;
}

