package com.aegis.aegisbackend.domain.event.dto;

import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.entity.EventAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {
    private String id;
    private String cameraId;
    private String cameraName;
    private String cameraLocation;
    private String risk;
    private String type;
    private String occurredAt;
    private String status;
    private String clipUrl;
    private String summary;
    private String riskScore;
    private List<ActionDto> actions;
    private List<Map<String, Object>> ragReferences;
    private String report;

    public static EventDto from(Event event) {
        return EventDto.builder()
                .id(event.getId().toString())
                .cameraId(event.getCamera().getId().toString())
                .cameraName(event.getCamera().getName())
                .cameraLocation(event.getCamera().getLocation())
                .risk(event.getRisk().getValue())
                .type(event.getType().getValue())
                .occurredAt(event.getOccurredAt().toString())
                .status(event.getStatus().getValue())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .riskScore(event.getRiskScore())
                .actions(event.getActions() != null
                        ? event.getActions().stream().map(ActionDto::from).toList()
                        : null)
                .ragReferences(event.getRagReferences())
                .report(event.getReport())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionDto {
        private String id;
        private String actionId;
        private String actionName;
        private Object inputParams;
        private String outputResult;
        private Boolean success;
        private String executedAt;

        public static ActionDto from(EventAction eventAction) {
            return ActionDto.builder()
                    .id(eventAction.getId().toString())
                    .actionId(eventAction.getAction().getId().toString())
                    .actionName(eventAction.getAction().getName())
                    .inputParams(eventAction.getInputParams())
                    .outputResult(eventAction.getOutputResult())
                    .success(eventAction.getSuccess())
                    .executedAt(eventAction.getExecutedAt().toString())
                    .build();
        }
    }
}
