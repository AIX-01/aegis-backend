package com.aegis.aegisbackend.domain.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class HeatmapData {
    private String title;
    @JsonProperty("yAxis")
    private List<String> yAxis;
    private List<HeatmapPoint> series;

    public HeatmapData(String title, List<String> yAxis, List<HeatmapPoint> series) {
        this.title = title;
        this.yAxis = yAxis;
        this.series = series;
    }
}
