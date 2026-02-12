package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class KpiData {
    private String totalEvents;
    private String totalEventsTrend;
    private Boolean totalEventsTrendUp;
    private String emergencyAlerts;
    private String emergencyAlertsTrend;
    private Boolean emergencyAlertsTrendUp;
    private String analysisCompletionRate;
    private String analysisCompletionRateTrend;
    private Boolean analysisCompletionRateTrendUp;
    private String monitoringCameras;
    private String monitoringCamerasUnit;
    private String monitoringCamerasTrend;
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
