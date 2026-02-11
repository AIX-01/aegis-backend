package com.aegis.aegisbackend.domain.stats.service;

import com.aegis.aegisbackend.domain.stats.dto.StatsDto.*;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;

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
                "BURGLARY", "절도",
                "DUMP", "투기",
                "SWOON", "실신",
                "VANDALISM", "파손"
        );

        List<EventTypeStats> stats = new ArrayList<>();

        for (Object[] row : results) {
            String type = row[0].toString();
            long count = ((Number) row[1]).longValue();

            stats.add(EventTypeStats.builder()
                    .type(typeNameMap.getOrDefault(type, type))
                    .count(count)
                    .build());
        }

        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, MonthlyData> getMonthlyStats() {
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        List<Object[]> eventResults = eventRepository.countByDateSince(monthAgo);
        List<Object[]> alertResults = eventRepository.countAlertsByDateSince(monthAgo);

        // alerts 데이터를 Map으로 변환
        Map<String, Long> alertsMap = new HashMap<>();
        for (Object[] row : alertResults) {
            String date = row[0].toString();
            long alerts = ((Number) row[1]).longValue();
            alertsMap.put(date, alerts);
        }

        Map<String, MonthlyData> monthlyStats = new LinkedHashMap<>();

        for (Object[] row : eventResults) {
            String date = row[0].toString();
            long events = ((Number) row[1]).longValue();
            long alerts = alertsMap.getOrDefault(date, 0L);

            monthlyStats.put(date, MonthlyData.builder()
                    .events(events)
                    .alerts(alerts)
                    .build());
        }

        return monthlyStats;
    }
}
