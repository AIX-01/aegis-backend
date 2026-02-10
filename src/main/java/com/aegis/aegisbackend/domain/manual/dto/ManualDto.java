package com.aegis.aegisbackend.domain.manual.dto;

import com.aegis.aegisbackend.domain.manual.entity.Manual;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 매뉴얼 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualDto {

    private String id;
    private String name;
    private String content;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity → DTO 변환
     */
    public static ManualDto from(Manual manual) {
        return ManualDto.builder()
                .id(manual.getId().toString())
                .name(manual.getName())
                .content(manual.getContent())
                .enabled(manual.getEnabled())
                .createdAt(manual.getCreatedAt())
                .updatedAt(manual.getUpdatedAt())
                .build();
    }

    /**
     * 매뉴얼 생성 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String name;
        private String content;
    }

    /**
     * 매뉴얼 수정 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private String content;
        private Boolean enabled;
    }

    /**
     * 임베딩 동기화 요청 (Python Agent로 전송)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmbeddingRequest {
        private String action;  // "create" | "update" | "delete"
        private ManualData manual;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ManualData {
            private String id;
            private String name;
            private String content;
            private Boolean enabled;
        }
    }
}

