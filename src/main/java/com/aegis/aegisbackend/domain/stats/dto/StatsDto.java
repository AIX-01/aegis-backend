package com.aegis.aegisbackend.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class StatsDto {

    // --- 공통 DTO ---

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventTypeDistributionDto {
        private String type;
        private long count;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CameraDistributionDto {
        private String cameraName;
        private long count;
    }

    // --- 일간 상세 분석용 DTO ---

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailySummaryDto {
        private long totalEvents;
        private List<CameraDistributionDto> cameraDistribution;
        private List<EventTypeDistributionDto> eventTypeDistribution;
    }

    // --- 기간별 통계용 DTO ---

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PeriodTrendDto {
        private String period; // 날짜, 월, 주차 등 X축 레이블
        private long totalEvents;
        private long resolvedEvents;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PeriodSummaryDto {
        private String period;
        private long totalEvents;
        private long resolvedEvents;
        private String topEventType;
        private long alerts;
    }

    // --- 기존 DTO (하위 호환성 유지 또는 삭제 예정) ---
    // 기존 프론트엔드 코드와의 호환성을 위해 당분간 유지할 수 있으나,
    // 새로운 API 구조로 완전히 전환되면 삭제하는 것이 좋습니다.
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
