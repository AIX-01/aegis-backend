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

    // 기본 날짜 범위 (전체 기간 조회용)
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 12, 31, 23, 59, 59);

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
        LocalDateTime start = (startDate != null) ? startDate : MIN_DATE;
        LocalDateTime end = (endDate != null) ? endDate : MAX_DATE;

        List<Object[]> results = eventRepository.findPeriodTrendBetween(start, end);
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
        LocalDateTime start = (startDate != null) ? startDate : MIN_DATE;
        LocalDateTime end = (endDate != null) ? endDate : MAX_DATE;

        List<Object[]> results = eventRepository.countEventTypeDistributionBetween(start, end);
        return results.stream()
                .map(row -> EventTypeDistributionDto.builder()
                        .type(EVENT_TYPE_NAME_MAP.getOrDefault(row[0].toString(), row[0].toString()))
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CameraDistributionDto> getCameraDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start = (startDate != null) ? startDate : MIN_DATE;
        LocalDateTime end = (endDate != null) ? endDate : MAX_DATE;

        List<Object[]> results = eventRepository.countCameraDistributionBetween(start, end);
        return results.stream()
                .map(row -> CameraDistributionDto.builder()
                        .cameraName(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PeriodSummaryDto getPeriodSummary(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime start = (startDate != null) ? startDate : MIN_DATE;
        LocalDateTime end = (endDate != null) ? endDate : MAX_DATE;

        long totalEvents = eventRepository.countTotalEventsBetween(start, end);
        long resolvedEvents = eventRepository.countResolvedEventsBetween(start, end);
        List<String> topEventTypes = eventRepository.findTopEventTypeBetween(start, end);
        long alerts = eventRepository.countAlertsBetween(start, end);

        String topEventType = topEventTypes.isEmpty() ? "-" : EVENT_TYPE_NAME_MAP.getOrDefault(topEventTypes.get(0), topEventTypes.get(0));

        return PeriodSummaryDto.builder()
                .period(formatPeriod(start, end))
                .totalEvents(totalEvents)
                .resolvedEvents(resolvedEvents)
                .topEventType(topEventType)
                .alerts(alerts)
                .build();
    }

    // 기간 포맷팅 헬퍼 메서드
    private String formatPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDate start = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        // 전체 기간인 경우 (MIN_DATE, MAX_DATE와 비교)
        if (start.getYear() == 1970 && end.getYear() == 2100) {
            return "전체 기간";
        }

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
}
