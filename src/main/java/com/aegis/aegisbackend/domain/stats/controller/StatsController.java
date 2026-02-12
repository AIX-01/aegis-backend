package com.aegis.aegisbackend.domain.stats.controller;

import com.aegis.aegisbackend.domain.stats.dto.StatisticsResponse;
import com.aegis.aegisbackend.domain.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/dashboard")
    public ResponseEntity<StatisticsResponse> getDashboardStats(
            @RequestParam(defaultValue = "day") String timeRange) {
        if (!"day".equals(timeRange) && !"week".equals(timeRange) && !"month".equals(timeRange)) {
            return ResponseEntity.badRequest().build();
        }
        StatisticsResponse response = statsService.getDashboardData(timeRange);
        return ResponseEntity.ok(response);
    }
}
