package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

/**
 * 이벤트 업데이트 요청 DTO (Agent → Spring)
 */
@Data
public class EventUpdateRequest {
    private String risk;
    private String type;
    private String summary;
    private String report;
    private String status;
}
