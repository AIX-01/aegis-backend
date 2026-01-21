package com.aegis.aegisbackend.infra.ai.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateEventRequest {
    private UUID cameraId;
    private String eventType;       // assault, burglary, dump, swoon, vandalism
    private String description;
    private String clipKey;         // 클립 추출에서 받은 키
    private String timestamp;       // optional, ISO8601 형식
}
