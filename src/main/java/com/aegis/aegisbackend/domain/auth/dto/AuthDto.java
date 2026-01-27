package com.aegis.aegisbackend.domain.auth.dto;

import com.aegis.aegisbackend.domain.user.dto.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 인증 관련 DTO
 */
public class AuthDto {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private UserDto user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
        private String password;

        @NotBlank(message = "이름은 필수입니다")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다")
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshResponse {
        private String accessToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordChangeRequest {
        @NotBlank(message = "현재 비밀번호는 필수입니다")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호는 필수입니다")
        @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileUpdateRequest {
        @NotBlank(message = "이름은 필수입니다")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다")
        private String name;
    }
}
