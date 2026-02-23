package com.aegis.aegisbackend.domain.stats.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class CameraRankData {
    private int rank;
    private String name;
    private int count;
    private boolean alert;

    public CameraRankData(int rank, String name, int count, boolean alert) {
        this.rank = rank;
        this.name = name;
        this.count = count;
        this.alert = alert;
    }
}
