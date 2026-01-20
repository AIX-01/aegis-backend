package com.aegis.aegisbackend.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {
    private String id;
    private String cameraId;
    private String cameraName;
    private String type; // "assault" | "theft" | "suspicious" | "normal"
    private String timestamp;
    private String status; // "processing" | "resolved"
    private String description;
    private String aiAction;
    private String clipUrl;
    private String summary;
    private String analysisReport;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String cameraId;
        private String type;
        private String timestamp;
        private String description;
        private String aiAction;
        private String summary;
        private String analysisReport;
        private byte[] clipData;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private String status;
    }
}
