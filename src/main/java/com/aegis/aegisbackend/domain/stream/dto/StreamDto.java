package com.aegis.aegisbackend.domain.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StreamDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamAccessResponse {
        private String streamUrl;   // WebRTC URL
        private String token;       // 일회용 토큰
        private String cameraId;
        private String cameraName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaMTXAuthRequest {
        private String user;        // 사용자 (Basic Auth)
        private String password;    // 비밀번호 (Basic Auth)
        private String ip;          // 클라이언트 IP
        private String action;      // "read" 또는 "publish"
        private String path;        // 카메라 경로 (스트림 이름)
        private String protocol;    // "webrtc", "hls", "rtsp" 등
        private String id;          // 연결 ID
        private String query;       // 쿼리 스트링
        private String jwt;         // JWT 토큰 (Authorization: Bearer 헤더)
    }
}
