package com.aegis.aegisbackend.domain.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class StatisticsResponse {

    private KpiData kpi;
    private TrendData trend;
    @JsonProperty("eventTypeDistribution")
    private DonutChartData eventTypeDistribution;
    private HeatmapData heatmap;
    @JsonProperty("topCameras")
    private List<CameraRankData> topCameras;

    public StatisticsResponse(KpiData kpi, TrendData trend, DonutChartData eventTypeDistribution, HeatmapData heatmap, List<CameraRankData> topCameras) {
        this.kpi = kpi;
        this.trend = trend;
        this.eventTypeDistribution = eventTypeDistribution;
        this.heatmap = heatmap;
        this.topCameras = topCameras;
    }
}
