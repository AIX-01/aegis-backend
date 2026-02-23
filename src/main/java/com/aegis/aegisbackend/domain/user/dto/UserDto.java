package com.aegis.aegisbackend.domain.user.dto;

import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String email;
    private String name;
    private String role; // "user" | "admin"
    private List<String> assignedCameras;
    private String createdAt;
    private boolean approved;

    /**
     * User 엔티티를 UserDto로 변환
     * @param user 사용자 엔티티
     * @param assignedCameras 할당된 카메라 ID 목록 (ADMIN인 경우 ["all"])
     */
    public static UserDto from(User user, List<String> assignedCameras) {
        return UserDto.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().getValue())
                .assignedCameras(assignedCameras)
                .createdAt(user.getCreatedAt().toString())
                .approved(user.getApproved())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private String role;
        private List<String> assignedCameras;
    }
}
