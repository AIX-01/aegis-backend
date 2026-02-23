package com.aegis.aegisbackend.domain.stream.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StreamDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaMTXAuthRequest {
        private String user;        // Basic Auth 사용자
        private String password;    // Basic Auth 비밀번호 (JWT)
        private String ip;          // 클라이언트 IP
        private String action;      // "read" 또는 "publish"
        private String path;        // 카메라 경로 (스트림 이름)
        private String protocol;    // "webrtc", "hls", "rtsp" 등
        private String id;          // 연결 ID
        private String query;       // 쿼리 스트링
        private String jwt;         // JWT 토큰 (Authorization: Bearer 헤더)
    }
}
