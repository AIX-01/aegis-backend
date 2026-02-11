package com.aegis.aegisbackend.domain.event.dto;

import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.entity.EventAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String clipUrl;
    private String summary;
    private String report;
    private String status;
    private List<ActionDto> actions;

    public static EventDto from(Event event) {
        return EventDto.builder()
                .id(event.getId().toString())
                .cameraId(event.getCamera().getId().toString())
                .cameraName(event.getCamera().getName())
                .cameraLocation(event.getCamera().getLocation())
                .risk(event.getRisk().getValue())
                .type(event.getType().getValue())
                .occurredAt(event.getOccurredAt().toString())
                .clipUrl(event.getClipUrl())
                .summary(event.getSummary())
                .report(event.getReport())
                .status(event.getStatus().getValue())
                .actions(event.getActions() != null
                        ? event.getActions().stream().map(ActionDto::from).toList()
                        : null)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionDto {
        private String id;
        private String userId;
        private String action;
        private String description;
        private String createdAt;

        public static ActionDto from(EventAction eventAction) {
            return ActionDto.builder()
                    .id(eventAction.getId().toString())
                    .userId(eventAction.getUser() != null ? eventAction.getUser().getId().toString() : null)
                    .action(eventAction.getAction())
                    .description(eventAction.getDescription())
                    .createdAt(eventAction.getCreatedAt().toString())
                    .build();
        }
    }
}
