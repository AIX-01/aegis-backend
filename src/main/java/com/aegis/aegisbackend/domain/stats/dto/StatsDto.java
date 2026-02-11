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
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonthlyData {
        private long events;
        private long alerts;
    }
}
