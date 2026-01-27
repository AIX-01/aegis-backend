package com.aegis.aegisbackend.domain.event.dto;

import com.aegis.aegisbackend.domain.event.entity.Event;
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
    private String type; // "assault" | "burglary" | "dump" | "swoon" | "vandalism"
    private String timestamp;
    private String status; // "processing" | "resolved"
    private String description;
    private String agentAction;
    private String clipUrl;
    private String summary;
    private String analysisReport;

    // Entity -> DTO 변환
    public static EventDto from(Event event) {
        return EventDto.builder()
                .id(event.getId().toString())
                .cameraId(event.getCamera().getId().toString())
                .cameraName(event.getCamera().getAlias())
                .type(event.getType().getValue())
                .timestamp(event.getTimestamp().toString())
                .status(event.getStatus().getValue())
                .description(event.getDescription())
                .agentAction(event.getAgentAction())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .analysisReport(event.getAnalysisReport())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String cameraId;
        private String type;
        private String timestamp;
        private String description;
        private String agentAction;
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
