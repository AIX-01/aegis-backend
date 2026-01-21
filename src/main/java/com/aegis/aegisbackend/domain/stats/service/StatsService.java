package com.aegis.aegisbackend.domain.stats.service;

import com.aegis.aegisbackend.domain.stats.dto.StatsDto.*;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;
    private final CameraRepository cameraRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<DailyStats> getDailyStats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> results = eventRepository.countByDayOfWeekSince(weekAgo);

        String[] dayNames = {"일", "월", "화", "수", "목", "금", "토"};
        Map<Integer, DailyStats> statsMap = new HashMap<>();

        // 기본값 초기화
        for (int i = 1; i <= 7; i++) {
            statsMap.put(i, DailyStats.builder()
                    .day(dayNames[i - 1])
                    .events(0L)
                    .resolved(0L)
                    .build());
        }

        // 실제 데이터로 업데이트
        for (Object[] row : results) {
            int dayOfWeek = ((Number) row[0]).intValue();
            long events = ((Number) row[1]).longValue();
            long resolved = ((Number) row[2]).longValue();

            statsMap.put(dayOfWeek, DailyStats.builder()
                    .day(dayNames[dayOfWeek - 1])
                    .events(events)
                    .resolved(resolved)
                    .build());
        }

        // 월요일부터 일요일 순서로 정렬
        List<DailyStats> result = new ArrayList<>();
        for (int i = 2; i <= 7; i++) { // 월~토
            result.add(statsMap.get(i));
        }
        result.add(statsMap.get(1)); // 일요일

        return result;
    }

    @Transactional(readOnly = true)
    public List<EventTypeStats> getEventTypeStats() {
        List<Object[]> results = eventRepository.countByEventType();

        Map<String, String> typeNameMap = Map.of(
                "ASSAULT", "폭행",
                "THEFT", "절도",
                "SUSPICIOUS", "의심 행동",
                "NORMAL", "정상"
        );

        Map<String, String> colorMap = Map.of(
                "ASSAULT", "hsl(var(--destructive))",
                "THEFT", "hsl(var(--warning))",
                "SUSPICIOUS", "hsl(var(--accent))",
                "NORMAL", "hsl(var(--success))"
        );

        List<EventTypeStats> stats = new ArrayList<>();

        for (Object[] row : results) {
            String type = row[0].toString();
            long count = ((Number) row[1]).longValue();

            stats.add(EventTypeStats.builder()
                    .type(typeNameMap.getOrDefault(type, type))
                    .count(count)
                    .color(colorMap.getOrDefault(type, "hsl(var(--muted))"))
                    .build());
        }

        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, MonthlyData> getMonthlyStats() {
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> results = eventRepository.countByDateSince(monthAgo);

        Map<String, MonthlyData> monthlyStats = new LinkedHashMap<>();

        for (Object[] row : results) {
            String date = row[0].toString();
            long events = ((Number) row[1]).longValue();

            // alerts는 ASSAULT와 THEFT 타입의 이벤트 수 (간단화를 위해 events의 20%로 가정)
            long alerts = (long) (events * 0.2);

            monthlyStats.put(date, MonthlyData.builder()
                    .events(events)
                    .alerts(alerts)
                    .build());
        }

        return monthlyStats;
    }

    @Transactional(readOnly = true)
    public SummaryStats getSummaryStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        LocalDate yesterday = today.minusDays(1);
        LocalDateTime yesterdayStart = yesterday.atStartOfDay();
        LocalDateTime yesterdayEnd = yesterday.atTime(LocalTime.MAX);

        // 오늘 이벤트 수
        long todayEvents = eventRepository.countByTimestampBetween(todayStart, todayEnd);
        long yesterdayEvents = eventRepository.countByTimestampBetween(yesterdayStart, yesterdayEnd);

        // 전일 대비 변화율 계산
        double todayEventsChange = 0;
        if (yesterdayEvents > 0) {
            todayEventsChange = ((double) (todayEvents - yesterdayEvents) / yesterdayEvents) * 100;
        }

        // AI 응답률 (처리 완료된 이벤트 비율)
        long totalEvents = eventRepository.count();
        long resolvedEvents = eventRepository.countByStatus(EventStatus.RESOLVED);
        double aiResponseRate = totalEvents > 0 ? ((double) resolvedEvents / totalEvents) * 100 : 0;

        // 활성 알림 수 (미해결 이벤트)
        long activeAlerts = eventRepository.countByStatus(EventStatus.PROCESSING);

        return SummaryStats.builder()
                .todayEvents(todayEvents)
                .aiResponseRate(Math.round(aiResponseRate * 10) / 10.0)
                .avgResponseTime(2.3) // 임시 고정값
                .activeAlerts(activeAlerts)
                .todayEventsChange(Math.round(todayEventsChange * 10) / 10.0)
                .aiResponseRateChange(2.1) // 임시 고정값
                .build();
    }
}

