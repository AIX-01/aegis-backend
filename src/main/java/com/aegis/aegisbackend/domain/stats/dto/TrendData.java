package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class TrendData {
    private String title;
    private List<String> xAxis;
    private List<Integer> series;

    public TrendData(String title, List<String> xAxis, List<Integer> series) {
        this.title = title;
        this.xAxis = xAxis;
        this.series = series;
    }
}
