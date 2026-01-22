package com.aegis.aegisbackend.infra.agent.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ClipRequest {
    private UUID cameraId;
    private Integer segmentCount;  // optional, 기본값 10
}
