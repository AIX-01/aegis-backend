package com.aegis.aegisbackend.domain.auth.controller;

import com.aegis.aegisbackend.domain.auth.dto.AuthDto.*;
import com.aegis.aegisbackend.domain.user.dto.UserDto;
import com.aegis.aegisbackend.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * 인증 컨트롤러
 * - 로그인, 회원가입, 로그아웃, 토큰 갱신
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7 days

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);

        // Refresh Token을 httpOnly 쿠키로 설정
        Cookie refreshCookie = new Cookie(REFRESH_TOKEN_COOKIE, loginResponse.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        response.addCookie(refreshCookie);

        // 응답에서 refreshToken 제거 (쿠키로만 전달)
        return ResponseEntity.ok(LoginResponse.builder()
                .accessToken(loginResponse.getAccessToken())
                .user(loginResponse.getUser())
                .build());
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원가입이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다."
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Boolean>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = getRefreshTokenFromCookie(request);

        if (userDetails != null) {
            UUID userId = UUID.fromString(userDetails.getUsername());
            authService.logout(userId, refreshToken);
        }

        // Refresh Token 쿠키 삭제
        Cookie deleteCookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        deleteCookie.setHttpOnly(true);
        deleteCookie.setSecure(true);
        deleteCookie.setPath("/");
        deleteCookie.setMaxAge(0);
        response.addCookie(deleteCookie);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request) {
        String refreshToken = getRefreshTokenFromCookie(request);
        RefreshResponse refreshResponse = authService.refresh(refreshToken);
        return ResponseEntity.ok(refreshResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UserDto user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PasswordChangeRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        authService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "비밀번호가 변경되었습니다."
        ));
    }

    /** 프로필 수정 (이름 변경) */
    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProfileUpdateRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UserDto user = authService.updateProfile(userId, request);
        return ResponseEntity.ok(user);
    }

    /** 회원탈퇴 (소프트 딜리트) */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        authService.deleteAccount(userId);

        // Refresh Token 쿠키 삭제
        Cookie deleteCookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        deleteCookie.setHttpOnly(true);
        deleteCookie.setSecure(true);
        deleteCookie.setPath("/");
        deleteCookie.setMaxAge(0);
        response.addCookie(deleteCookie);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원탈퇴가 완료되었습니다."
        ));
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
