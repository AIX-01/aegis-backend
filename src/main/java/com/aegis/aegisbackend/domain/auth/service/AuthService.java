package com.aegis.aegisbackend.domain.auth.service;

import com.aegis.aegisbackend.domain.auth.dto.AuthDto.*;
import com.aegis.aegisbackend.domain.user.dto.UserDto;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.global.common.enums.UserRole;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.domain.camera.repository.UserCameraRepository;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.global.security.JwtTokenProvider;
import com.aegis.aegisbackend.infra.redis.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 인증 서비스
 * - 로그인/회원가입/로그아웃
 * - 토큰 갱신
 * - 비밀번호 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCameraRepository userCameraRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (!user.getApproved()) {
            throw new BusinessException(ErrorCode.USER_NOT_APPROVED);
        }
        if (user.getDeleted()) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtTokenProvider.createRefreshToken();
        redisTokenService.saveRefreshToken(refreshToken, user.getId(),
                jwtTokenProvider.getRefreshExpiration());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(UserRole.USER)
                .approved(false)
                .build();

        userRepository.save(user);
        log.info("회원가입 완료: {}", request.getEmail());
    }

    @Transactional
    public void logout(UUID userId, String refreshToken) {
        if (refreshToken != null) {
            redisTokenService.deleteRefreshToken(refreshToken);
        }
        log.info("로그아웃: userId={}", userId);
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        String userIdStr = redisTokenService.getUserIdByRefreshToken(refreshToken);
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_USER));

        if (!user.getApproved()) {
            throw new BusinessException(ErrorCode.INVALID_USER);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findByIdWithCameras(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toUserDto(user);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("비밀번호 변경: userId={}", userId);
    }

    /** 프로필 수정 */
    @Transactional
    public UserDto updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = userRepository.findByIdWithCameras(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        userRepository.save(user);
        log.info("프로필 수정: userId={}", userId);
        return toUserDto(user);
    }

    /** 회원탈퇴 (소프트 딜리트) */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("회원탈퇴: userId={}", userId);
    }

    // === Private ===

    private UserDto toUserDto(User user) {
        List<String> assignedCameras = user.getRole() == UserRole.ADMIN
                ? List.of("all")
                : userCameraRepository.findCameraIdsByUserId(user.getId())
                        .stream().map(UUID::toString).toList();

        return UserDto.from(user, assignedCameras);
    }
}
