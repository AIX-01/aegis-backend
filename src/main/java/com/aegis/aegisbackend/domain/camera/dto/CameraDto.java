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
    private String location;
    private Boolean enabled;
    private Boolean analysisEnabled;
    private String streamUrl;  // WebRTC WHEP URL

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String location;
        private Boolean enabled;
        private Boolean analysisEnabled;
    }
}

