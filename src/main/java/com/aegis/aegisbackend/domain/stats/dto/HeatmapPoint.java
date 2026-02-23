package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class HeatmapPoint {
    private int x;
    private int y;
    private int value;

    public HeatmapPoint(int x, int y, int value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }
}
