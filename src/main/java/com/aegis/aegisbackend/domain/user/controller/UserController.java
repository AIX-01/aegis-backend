package com.aegis.aegisbackend.domain.user.controller;

import com.aegis.aegisbackend.domain.user.dto.UserDto;
import com.aegis.aegisbackend.domain.user.service.UserService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 사용자 관리 API (관리자 전용)
 * - 사용자 목록 조회 (페이지네이션), 승인, 카메라 할당
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * 승인된 사용자 목록 조회 (관리자→일반 순, 이메일순 정렬)
     */
    @GetMapping
    public ResponseEntity<?> getApprovedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<UserDto> users = userService.getApprovedUsersPaged(page, size);
        return ResponseEntity.ok(users);
    }

    /**
     * 미승인 사용자 목록 조회 (최신 가입순 정렬)
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<UserDto> users = userService.getPendingUsersPaged(page, size);
        return ResponseEntity.ok(users);
    }

    /**
     * 미승인 사용자 수 조회
     */
    @GetMapping("/pending/count")
    public ResponseEntity<Map<String, Long>> getPendingUsersCount() {
        long count = userService.countPendingUsers();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        UserDto user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID id,
            @RequestBody UserDto.UpdateRequest request) {
        UserDto user = userService.updateUser(id, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<UserDto> approveUser(@PathVariable UUID id) {
        UserDto user = userService.approveUser(id);
        return ResponseEntity.ok(user);
    }
}
