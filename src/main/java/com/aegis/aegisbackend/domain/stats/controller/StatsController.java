package com.aegis.aegisbackend.domain.stats.controller;

import com.aegis.aegisbackend.domain.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<?> getStats(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Controller에서는 날짜 변환 및 null 체크만 수행하고, 비즈니스 로직은 Service로 위임
        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        // endDate는 해당 날짜의 끝까지 포함하도록 설정
        LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

        return switch (type) {
            case "daily-summary" -> {
                if (startDateTime == null) {
                    yield ResponseEntity.badRequest().body("Date is required for daily-summary");
                }
                yield ResponseEntity.ok(statsService.getDailySummary(startDateTime));
            }
            case "period-trend" -> ResponseEntity.ok(statsService.getPeriodTrend(startDateTime, endDateTime));
            case "event-type-distribution" -> ResponseEntity.ok(statsService.getEventTypeDistribution(startDateTime, endDateTime));
            case "camera-distribution" -> ResponseEntity.ok(statsService.getCameraDistribution(startDateTime, endDateTime));
            case "period-summary" -> ResponseEntity.ok(statsService.getPeriodSummary(startDateTime, endDateTime));
            default -> ResponseEntity.badRequest().body("Invalid stat type: " + type);
        };
    }
}
