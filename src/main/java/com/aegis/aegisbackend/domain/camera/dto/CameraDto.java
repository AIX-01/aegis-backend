package com.aegis.aegisbackend.domain.camera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraDto {
    private String id;
    private String name;
    private Boolean connected;
    private String alias;
    private Boolean enabled;
    private Boolean analysisEnabled;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String alias;
        private Boolean enabled;
        private Boolean analysisEnabled;
    }
}

