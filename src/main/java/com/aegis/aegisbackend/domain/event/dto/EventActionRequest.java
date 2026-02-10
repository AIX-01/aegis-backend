package com.aegis.aegisbackend.domain.event.dto;

import lombok.*;

import java.util.Map;

/**
 * EventAction 기록 요청 DTO
 * - Python Agent에서 Tool 실행 결과 기록 시 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventActionRequest {

    private String actionId;
    private Map<String, Object> inputParams;
    private String outputResult;
    private Boolean success;
    private String executedAt;  // ISO 8601 형식
}

