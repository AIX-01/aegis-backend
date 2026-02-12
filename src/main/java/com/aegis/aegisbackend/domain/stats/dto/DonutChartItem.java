package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class DonutChartItem {
    private String type;
    private int count;
    private double percentage;

    public DonutChartItem(String type, int count, double percentage) {
        this.type = type;
        this.count = count;
        this.percentage = percentage;
    }
}
