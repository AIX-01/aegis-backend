package com.aegis.aegisbackend.domain.stats.service;

import com.aegis.aegisbackend.domain.stats.dto.StatsDto.*;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;

    // 이벤트 유형 한글 매핑
    private static final Map<String, String> EVENT_TYPE_NAME_MAP = Map.of(
            "ASSAULT", "폭행",
            "BURGLARY", "절도",
            "DUMP", "투기",
            "SWOON", "실신",
            "VANDALISM", "파손"
    );

    // --- 새로운 통계 서비스 메서드 ---

    @Transactional(readOnly = true)
    public DailySummaryDto getDailySummary(LocalDateTime date) {
        LocalDateTime startOfDay = date.with(LocalTime.MIN);
        LocalDateTime endOfDay = date.with(LocalTime.MAX);

        long totalEvents = eventRepository.countTotalEventsBetween(startOfDay, endOfDay);

        List<CameraDistributionDto> cameraDistribution = eventRepository.countCameraDistributionBetween(startOfDay, endOfDay).stream()
                .map(row -> CameraDistributionDto.builder()
                        .cameraName(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        List<EventTypeDistributionDto> eventTypeDistribution = eventRepository.countEventTypeDistributionBetween(startOfDay, endOfDay).stream()
                .map(row -> EventTypeDistributionDto.builder()
                        .type(EVENT_TYPE_NAME_MAP.getOrDefault(row[0].toString(), row[0].toString()))
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return DailySummaryDto.builder()
                .totalEvents(totalEvents)
                .cameraDistribution(cameraDistribution)
                .eventTypeDistribution(eventTypeDistribution)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PeriodTrendDto> getPeriodTrend(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = eventRepository.findPeriodTrendBetween(startDate, endDate);
        return results.stream()
                .map(row -> PeriodTrendDto.builder()
                        .period(row[0].toString()) // DATE(e.occurred_at) 결과는 String (yyyy-MM-dd)
                        .totalEvents(((Number) row[1]).longValue())
                        .resolvedEvents(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventTypeDistributionDto> getEventTypeDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = eventRepository.countEventTypeDistributionBetween(startDate, endDate);
        return results.stream()
                .map(row -> EventTypeDistributionDto.builder()
                        .type(EVENT_TYPE_NAME_MAP.getOrDefault(row[0].toString(), row[0].toString()))
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CameraDistributionDto> getCameraDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = eventRepository.countCameraDistributionBetween(startDate, endDate);
        return results.stream()
                .map(row -> CameraDistributionDto.builder()
                        .cameraName(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PeriodSummaryDto getPeriodSummary(LocalDateTime startDate, LocalDateTime endDate) {
        long totalEvents = eventRepository.countTotalEventsBetween(startDate, endDate);
        long resolvedEvents = eventRepository.countResolvedEventsBetween(startDate, endDate);
        List<String> topEventTypes = eventRepository.findTopEventTypeBetween(startDate, endDate);
        long alerts = eventRepository.countAlertsBetween(startDate, endDate);

        String topEventType = topEventTypes.isEmpty() ? "-" : EVENT_TYPE_NAME_MAP.getOrDefault(topEventTypes.get(0), topEventTypes.get(0));

        return PeriodSummaryDto.builder()
                .period(formatPeriod(startDate, endDate)) // 기간 포맷팅 로직 추가
                .totalEvents(totalEvents)
                .resolvedEvents(resolvedEvents)
                .topEventType(topEventType)
                .alerts(alerts)
                .build();
    }

    // 기간 포맷팅 헬퍼 메서드 (예시)
    private String formatPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDate start = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        if (start.isEqual(end)) {
            return start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return start.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } else if (start.getYear() == end.getYear()) {
            return start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " ~ " + end.format(DateTimeFormatter.ofPattern("MM-dd"));
        } else {
            return start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " ~ " + end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }

    // --- 기존 통계 서비스 메서드 (삭제) ---
    // 기존 메서드들은 새로운 구조로 대체되었으므로 삭제합니다.
}
