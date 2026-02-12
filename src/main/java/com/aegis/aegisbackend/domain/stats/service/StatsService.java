package com.aegis.aegisbackend.domain.stats.service;

import com.aegis.aegisbackend.domain.camera.entity.Camera;
import com.aegis.aegisbackend.domain.camera.repository.CameraRepository;
import com.aegis.aegisbackend.domain.event.entity.Event;
import com.aegis.aegisbackend.domain.event.repository.EventRepository;
import com.aegis.aegisbackend.domain.stats.dto.*;
import com.aegis.aegisbackend.global.common.enums.EventRisk;
import com.aegis.aegisbackend.global.common.enums.EventStatus;
import com.aegis.aegisbackend.global.common.enums.EventType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final EventRepository eventRepository;
    private final CameraRepository cameraRepository;

    @Transactional(readOnly = true)
    public StatisticsResponse getDashboardData(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfCurrentPeriod;
        LocalDateTime endOfCurrentPeriod;
        LocalDateTime startOfPreviousPeriod;
        LocalDateTime endOfPreviousPeriod;

        switch (timeRange) {
            case "week":
                startOfCurrentPeriod = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay();
                endOfCurrentPeriod = startOfCurrentPeriod.plusDays(7).minusNanos(1);
                startOfPreviousPeriod = startOfCurrentPeriod.minusWeeks(1);
                endOfPreviousPeriod = endOfCurrentPeriod.minusWeeks(1);
                break;
            case "month":
                startOfCurrentPeriod = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
                endOfCurrentPeriod = now.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(LocalTime.MAX);
                startOfPreviousPeriod = startOfCurrentPeriod.minusMonths(1);
                endOfPreviousPeriod = endOfCurrentPeriod.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().atTime(LocalTime.MAX);
                break;
            case "day":
            default:
                startOfCurrentPeriod = now.toLocalDate().atStartOfDay();
                endOfCurrentPeriod = now.toLocalDate().atTime(LocalTime.MAX);
                startOfPreviousPeriod = startOfCurrentPeriod.minusDays(1);
                endOfPreviousPeriod = endOfCurrentPeriod.minusDays(1);
                break;
        }

        List<Event> currentEvents = findEventsBetween(startOfCurrentPeriod, endOfCurrentPeriod);
        List<Event> previousEvents = findEventsBetween(startOfPreviousPeriod, endOfPreviousPeriod);

        KpiData kpiData = buildKpiData(currentEvents, previousEvents, timeRange);
        TrendData trendData = buildTrendData(currentEvents, timeRange, startOfCurrentPeriod);
        DonutChartData donutChartData = buildDonutChartData(currentEvents);
        HeatmapData heatmapData = buildHeatmapData(currentEvents, timeRange);
        List<CameraRankData> topCameras = buildTopCamerasData(currentEvents);

        return new StatisticsResponse(kpiData, trendData, donutChartData, heatmapData, topCameras);
    }

    private List<Event> findEventsBetween(LocalDateTime start, LocalDateTime end) {
        Specification<Event> spec = (root, query, cb) -> {
            Predicate predicate = cb.between(root.get("occurredAt"), start, end);
            return predicate;
        };
        return eventRepository.findAll(spec);
    }

    private KpiData buildKpiData(List<Event> currentEvents, List<Event> previousEvents, String timeRange) {
        // Total Events
        long currentTotal = currentEvents.size();
        long previousTotal = previousEvents.size();

        // Emergency Alerts
        long currentEmergency = currentEvents.stream().filter(e -> e.getRisk() == EventRisk.ABNORMAL || e.getRisk() == EventRisk.SUSPICIOUS).count();
        long previousEmergency = previousEvents.stream().filter(e -> e.getRisk() == EventRisk.ABNORMAL || e.getRisk() == EventRisk.SUSPICIOUS).count();

        // Analysis Completion Rate
        long currentAnalyzed = currentEvents.stream().filter(e -> e.getStatus() == EventStatus.ANALYZED).count();
        double currentRate = (currentTotal == 0) ? 100.0 : (double) currentAnalyzed * 100 / currentTotal;

        long previousAnalyzed = previousEvents.stream().filter(e -> e.getStatus() == EventStatus.ANALYZED).count();
        double previousRate = (previousEvents.size() == 0) ? 100.0 : (double) previousAnalyzed * 100 / previousEvents.size();
        double rateDiff = currentRate - previousRate;

        String rateTrend;
        if (Math.abs(rateDiff) < 0.1) {
            rateTrend = "변동 없음";
        } else {
            rateTrend = String.format("%s%.1f%%", rateDiff > 0 ? "+" : "", rateDiff);
        }

        // Monitoring Cameras
        List<Camera> allCameras = cameraRepository.findAll();
        long totalCameras = allCameras.size();
        long activeCameras = allCameras.stream().filter(Camera::getConnected).count();

        return new KpiData(
                String.format("%,d", currentTotal),
                getTrendString(currentTotal, previousTotal, "건", timeRange),
                currentTotal >= previousTotal,
                String.format("%,d", currentEmergency),
                getTrendString(currentEmergency, previousEmergency, "건", timeRange),
                currentEmergency >= previousEmergency,
                String.format("%.1f", currentRate),
                rateTrend,
                rateDiff >= 0,
                String.valueOf(activeCameras),
                "/ " + totalCameras + " 대",
                activeCameras == totalCameras ? "모두 정상 작동중" : (totalCameras - activeCameras) + "대 확인 필요",
                null
        );
    }

    private String getTrendString(long current, long previous, String unit, String timeRange) {
        long diff = current - previous;
        if (diff == 0) return "변동 없음";

        String periodStr = switch (timeRange) {
            case "day" -> "전일";
            case "week" -> "전주";
            case "month" -> "전월";
            default -> "이전";
        };

        return String.format("%s%d%s (%s 대비)", diff > 0 ? "+" : "", diff, unit, periodStr);
    }

    private TrendData buildTrendData(List<Event> events, String timeRange, LocalDateTime start) {
        String title = "";
        List<String> xAxis = new ArrayList<>();
        List<Integer> series = new ArrayList<>();

        switch (timeRange) {
            case "day":
                title = "시간대별 이벤트 추이";
                xAxis = Arrays.asList("0-4시", "4-8시", "8-12시", "12-16시", "16-20시", "20-24시");
                Map<Integer, Long> hourly = events.stream()
                        .collect(Collectors.groupingBy(
                                e -> e.getOccurredAt().getHour() / 4, // 4시간 단위로 그룹화 (0-5)
                                Collectors.counting()
                        ));
                series = IntStream.range(0, 6) // 6개 구간
                        .map(interval -> hourly.getOrDefault(interval, 0L).intValue())
                        .boxed()
                        .collect(Collectors.toList());
                break;
            case "week":
                title = "요일별 이벤트 추이";
                xAxis = Arrays.asList("월", "화", "수", "목", "금", "토", "일");
                Map<DayOfWeek, Long> daily = events.stream().collect(Collectors.groupingBy(e -> e.getOccurredAt().getDayOfWeek(), Collectors.counting()));
                series = Arrays.stream(DayOfWeek.values()).map(d -> daily.getOrDefault(d, 0L).intValue()).collect(Collectors.toList());
                break;
            case "month":
                title = "일별 이벤트 추이";
                int daysInMonth = start.toLocalDate().lengthOfMonth();
                xAxis = IntStream.rangeClosed(1, daysInMonth).mapToObj(String::valueOf).collect(Collectors.toList());
                Map<Integer, Long> monthly = events.stream().collect(Collectors.groupingBy(e -> e.getOccurredAt().getDayOfMonth(), Collectors.counting()));
                series = IntStream.rangeClosed(1, daysInMonth).map(d -> monthly.getOrDefault(d, 0L).intValue()).boxed().collect(Collectors.toList());
                break;
        }
        return new TrendData(title, xAxis, series);
    }

    private DonutChartData buildDonutChartData(List<Event> events) {
        if (events.isEmpty()) {
            return new DonutChartData(new ArrayList<>());
        }
        Map<EventType, Long> typeCounts = events.stream()
                .collect(Collectors.groupingBy(Event::getType, Collectors.counting()));

        long total = events.size();

        List<DonutChartItem> items = typeCounts.entrySet().stream()
                .map(entry -> new DonutChartItem(
                        entry.getKey().name(),
                        entry.getValue().intValue(),
                        (double) entry.getValue() * 100 / total
                ))
                .collect(Collectors.toList());

        return new DonutChartData(items);
    }

    private HeatmapData buildHeatmapData(List<Event> events, String timeRange) {
        String title = "";
        List<String> yAxis = new ArrayList<>();
        List<HeatmapPoint> series = new ArrayList<>();

        switch (timeRange) {
            case "day":
                title = "구역/시간대별 집중도";
                List<Camera> allCameras = cameraRepository.findAll();
                yAxis = allCameras.stream().map(Camera::getLocation).distinct().collect(Collectors.toList());
                Map<String, Integer> yAxisMap = IntStream.range(0, yAxis.size()).boxed().collect(Collectors.toMap(yAxis::get, Function.identity()));

                Map<String, Map<Integer, Long>> locationHourly = events.stream()
                        .collect(Collectors.groupingBy(e -> e.getCamera().getLocation(),
                                Collectors.groupingBy(e -> e.getOccurredAt().getHour() / 6, Collectors.counting())));

                series = locationHourly.entrySet().stream()
                        .flatMap(entry -> entry.getValue().entrySet().stream()
                                .map(innerEntry -> new HeatmapPoint(
                                        innerEntry.getKey(),
                                        yAxisMap.getOrDefault(entry.getKey(), -1),
                                        innerEntry.getValue().intValue()
                                )))
                        .filter(p -> p.getY() != -1)
                        .collect(Collectors.toList());
                break;
            case "week":
            case "month":
                title = "요일/시간대별 발생 패턴";
                yAxis = Arrays.asList("월", "화", "수", "목", "금", "토", "일");
                Map<DayOfWeek, Map<Integer, Long>> dayHourly = events.stream()
                        .collect(Collectors.groupingBy(e -> e.getOccurredAt().getDayOfWeek(),
                                Collectors.groupingBy(e -> e.getOccurredAt().getHour() / 6, Collectors.counting())));

                series = dayHourly.entrySet().stream()
                        .flatMap(entry -> entry.getValue().entrySet().stream()
                                .map(innerEntry -> new HeatmapPoint(
                                        innerEntry.getKey(),
                                        entry.getKey().getValue() - 1, // DayOfWeek is 1-7
                                        innerEntry.getValue().intValue()
                                )))
                        .collect(Collectors.toList());
                break;
        }
        return new HeatmapData(title, yAxis, series);
    }

    private List<CameraRankData> buildTopCamerasData(List<Event> events) {
        Map<Camera, Long> cameraCounts = events.stream()
                .collect(Collectors.groupingBy(Event::getCamera, Collectors.counting()));

        Map<Camera, Boolean> cameraAlerts = events.stream()
                .filter(e -> e.getRisk() == EventRisk.ABNORMAL)
                .collect(Collectors.toMap(Event::getCamera, v -> true, (v1, v2) -> v1));

        List<CameraRankData> ranks = cameraCounts.entrySet().stream()
                .sorted(Map.Entry.<Camera, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new CameraRankData(
                        0, // Rank will be set below
                        entry.getKey().getName() + " (" + entry.getKey().getLocation() + ")",
                        entry.getValue().intValue(),
                        cameraAlerts.getOrDefault(entry.getKey(), false)
                ))
                .collect(Collectors.toList());

        IntStream.range(0, ranks.size()).forEach(i -> ranks.get(i).setRank(i + 1));
        return ranks;
    }
}
