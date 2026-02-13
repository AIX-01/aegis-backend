package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class DonutChartData {

    private List<DonutChartItem> items;

    public DonutChartData(List<DonutChartItem> items) {
        this.items = items;
    }
}
