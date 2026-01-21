package com.aegis.aegisbackend.domain.stats.controller;

import com.aegis.aegisbackend.domain.stats.dto.StatsDto.*;
import com.aegis.aegisbackend.domain.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 통계 API
 * - 이벤트 통계, 카메라 현황, 스토리지 사용량
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<?> getStats(@RequestParam(required = false) String type) {
        if (type == null) {
            // 전체 통계 반환
            Map<String, Object> allStats = new HashMap<>();
            allStats.put("daily", statsService.getDailyStats());
            allStats.put("eventTypes", statsService.getEventTypeStats());
            allStats.put("monthly", statsService.getMonthlyStats());
            return ResponseEntity.ok(allStats);
        }

        return switch (type) {
            case "daily" -> ResponseEntity.ok(statsService.getDailyStats());
            case "event-types" -> ResponseEntity.ok(statsService.getEventTypeStats());
            case "monthly" -> ResponseEntity.ok(statsService.getMonthlyStats());
            default -> ResponseEntity.badRequest().body(Map.of("error", "Invalid stat type"));
        };
    }
}
