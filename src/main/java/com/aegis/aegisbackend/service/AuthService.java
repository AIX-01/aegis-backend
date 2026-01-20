package com.aegis.aegisbackend.service;

import com.aegis.aegisbackend.dto.AuthDto.*;
import com.aegis.aegisbackend.dto.UserDto;
import com.aegis.aegisbackend.entity.User;
import com.aegis.aegisbackend.entity.enums.UserRole;
import com.aegis.aegisbackend.exception.AegisException;
import com.aegis.aegisbackend.exception.ErrorCode;
import com.aegis.aegisbackend.repository.UserCameraRepository;
import com.aegis.aegisbackend.repository.UserRepository;
import com.aegis.aegisbackend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AegisException(ErrorCode.EMAIL_NOT_FOUND));

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AegisException(ErrorCode.INVALID_PASSWORD);
        }

        // 3. 승인 여부 확인
        if (!user.getApproved()) {
            throw new AegisException(ErrorCode.USER_NOT_APPROVED);
        }

        // 4. Access Token 생성
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        // 5. Refresh Token 생성 및 Redis 저장
        String refreshToken = jwtTokenProvider.createRefreshToken();
        redisTokenService.saveRefreshToken(
                refreshToken,
                user.getId(),
                jwtTokenProvider.getRefreshExpiration()
        );


        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserDto(user))
                .build();
    }

    @Transactional
    public void signup(SignupRequest request) {
        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AegisException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 2. 사용자 생성 (approved = false)
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(UserRole.USER)
                .approved(false)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", request.getEmail());
    }

    @Transactional
    public void logout(UUID userId, String refreshToken) {
        // Redis에서 Refresh Token 삭제
        if (refreshToken != null) {
            redisTokenService.deleteRefreshToken(refreshToken);
        }
        log.info("User logged out: {}", userId);
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new AegisException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        // 1. Refresh Token으로 사용자 ID 조회
        String userIdStr = redisTokenService.getUserIdByRefreshToken(refreshToken);
        if (userIdStr == null) {
            throw new AegisException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId = UUID.fromString(userIdStr);

        // 2. 사용자 조회 및 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.INVALID_USER));

        if (!user.getApproved()) {
            throw new AegisException(ErrorCode.INVALID_USER);
        }

        // 3. 새 Access Token 생성 (JWT 서명으로 검증, Redis 저장 안 함)
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );


        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findByIdWithCameras(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        return toUserDto(user);
    }

    @Transactional
    public void changePassword(UUID userId, PasswordChangeRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AegisException(ErrorCode.USER_NOT_FOUND));

        // 2. 현재 비밀번호 검증
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AegisException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        // 3. 새 비밀번호 길이 검증
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new AegisException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        // 4. 비밀번호 변경
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", userId);
    }

    private UserDto toUserDto(User user) {
        List<String> assignedCameras;

        if (user.getRole() == UserRole.ADMIN) {
            assignedCameras = List.of("all");
        } else {
            assignedCameras = userCameraRepository.findCameraIdsByUserId(user.getId())
                    .stream()
                    .map(UUID::toString)
                    .toList();
        }

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
}

