package com.aegis.aegisbackend.domain.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class KpiData {
    @JsonProperty("totalEvents")
    private String totalEvents;
    @JsonProperty("totalEventsTrend")
    private String totalEventsTrend;
    @JsonProperty("totalEventsTrendUp")
    private Boolean totalEventsTrendUp;
    @JsonProperty("emergencyAlerts")
    private String emergencyAlerts;
    @JsonProperty("emergencyAlertsTrend")
    private String emergencyAlertsTrend;
    @JsonProperty("emergencyAlertsTrendUp")
    private Boolean emergencyAlertsTrendUp;
    @JsonProperty("analysisCompletionRate")
    private String analysisCompletionRate;
    @JsonProperty("analysisCompletionRateTrend")
    private String analysisCompletionRateTrend;
    @JsonProperty("analysisCompletionRateTrendUp")
    private Boolean analysisCompletionRateTrendUp;
    @JsonProperty("monitoringCameras")
    private String monitoringCameras;
    @JsonProperty("monitoringCamerasUnit")
    private String monitoringCamerasUnit;
    @JsonProperty("monitoringCamerasTrend")
    private String monitoringCamerasTrend;
    @JsonProperty("monitoringCamerasTrendUp")
    private Boolean monitoringCamerasTrendUp;

    public KpiData(String totalEvents, String totalEventsTrend, Boolean totalEventsTrendUp, String emergencyAlerts, String emergencyAlertsTrend, Boolean emergencyAlertsTrendUp, String analysisCompletionRate, String analysisCompletionRateTrend, Boolean analysisCompletionRateTrendUp, String monitoringCameras, String monitoringCamerasUnit, String monitoringCamerasTrend, Boolean monitoringCamerasTrendUp) {
        this.totalEvents = totalEvents;
        this.totalEventsTrend = totalEventsTrend;
        this.totalEventsTrendUp = totalEventsTrendUp;
        this.emergencyAlerts = emergencyAlerts;
        this.emergencyAlertsTrend = emergencyAlertsTrend;
        this.emergencyAlertsTrendUp = emergencyAlertsTrendUp;
        this.analysisCompletionRate = analysisCompletionRate;
        this.analysisCompletionRateTrend = analysisCompletionRateTrend;
        this.analysisCompletionRateTrendUp = analysisCompletionRateTrendUp;
        this.monitoringCameras = monitoringCameras;
        this.monitoringCamerasUnit = monitoringCamerasUnit;
        this.monitoringCamerasTrend = monitoringCamerasTrend;
        this.monitoringCamerasTrendUp = monitoringCamerasTrendUp;
    }
}
