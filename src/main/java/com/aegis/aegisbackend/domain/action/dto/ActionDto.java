package com.aegis.aegisbackend.domain.action.dto;

import com.aegis.aegisbackend.domain.action.entity.Action;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 액션 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionDto {

    private String id;
    private String name;
    private String description;
    private Map<String, Object> parameters;
    private String code;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity → DTO 변환
     */
    public static ActionDto from(Action action) {
        return ActionDto.builder()
                .id(action.getId().toString())
                .name(action.getName())
                .description(action.getDescription())
                .parameters(action.getParameters())
                .code(action.getCode())
                .enabled(action.getEnabled())
                .createdAt(action.getCreatedAt())
                .updatedAt(action.getUpdatedAt())
                .build();
    }

    /**
     * 액션 생성 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        private String code;
    }

    /**
     * 액션 수정 요청
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        private String code;
        private Boolean enabled;
    }
}

