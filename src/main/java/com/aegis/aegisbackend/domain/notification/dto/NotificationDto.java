package com.aegis.aegisbackend.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String id;
    private String type; // "alert" | "warning" | "info" | "success"
    private String title;
    private String message;
    private String timestamp;
    private String eventId;
}
