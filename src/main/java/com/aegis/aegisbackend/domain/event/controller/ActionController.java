package com.aegis.aegisbackend.domain.event.controller;

import com.aegis.aegisbackend.domain.notification.service.SseEmitterService;
import com.aegis.aegisbackend.domain.user.entity.User;
import com.aegis.aegisbackend.domain.user.repository.UserRepository;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.agent.dto.PendingActionResponse;
import com.aegis.aegisbackend.infra.agent.service.PendingActionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 액션 API (프론트엔드용)
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class ActionController {

    private final PendingActionService pendingActionService;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * 액션 승인/거부
     */
    @PostMapping("/{eventId}/actions/{actionId}/resolve")
    public ResponseEntity<?> resolveAction(
            @PathVariable UUID eventId,
            @PathVariable UUID actionId,
            @RequestBody ResolveRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        log.info("액션 resolve 요청: eventId={}, actionId={}, approved={}, userId={}",
                eventId, actionId, request.isApproved(), userId);

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Pending 액션 해결
        PendingActionResponse response;
        if (request.isApproved()) {
            response = PendingActionResponse.approved(
                    userId.toString(),
                    user.getName(),
                    user.getEmail()
            );
        } else {
            response = PendingActionResponse.rejected(
                    userId.toString(),
                    user.getName(),
                    user.getEmail()
            );
        }

        boolean resolved = pendingActionService.resolve(actionId, response);
        if (!resolved) {
            log.warn("Pending 액션을 찾을 수 없거나 이미 처리됨: actionId={}", actionId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "액션을 찾을 수 없거나 이미 처리되었습니다"));
        }

        // SSE로 다른 사용자들에게 알림 (UI 제거용)
        sseEmitterService.broadcastActionResolved(eventId, actionId);

        return ResponseEntity.ok(Map.of("success", true));
    }

    @Data
    public static class ResolveRequest {
        private boolean approved;
    }
}

