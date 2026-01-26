package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

/**
 * 이벤트 생성 요청 DTO
 * - Agent가 이상상황 감지 시 호출
 * - 이벤트 = 메타데이터 + 클립 영상이 함께 포함된 단일 객체
 */
@Data
public class CreateEventRequest {
    private String cameraName;      // MediaMTX 스트림 경로명 (실명, 예: cam1)
    private String eventType;       // assault, burglary, dump, swoon, vandalism
    private String timestamp;       // optional, ISO8601 형식 (없으면 현재 시간)
}
