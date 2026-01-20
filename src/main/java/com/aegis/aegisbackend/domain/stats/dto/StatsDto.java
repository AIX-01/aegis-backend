package com.aegis.aegisbackend.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StatsDto {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyStats {
        private String day;
        private long events;
        private long resolved;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventTypeStats {
        private String type;
        private long count;
        private String color;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonthlyData {
        private long events;
        private long alerts;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SummaryStats {
        private long todayEvents;
        private double aiResponseRate;
        private double avgResponseTime;
        private long activeAlerts;
        private double todayEventsChange;
        private double aiResponseRateChange;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemStatus {
        private String status;
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StorageInfo {
        private long usedStorage;
        private long totalStorage;
    }
}
