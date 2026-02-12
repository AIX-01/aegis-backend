package com.aegis.aegisbackend.infra.agent.service;

import com.aegis.aegisbackend.infra.agent.dto.PendingActionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending Action 메모리 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    private final ConcurrentHashMap<UUID, PendingActionContext> pendingActions = new ConcurrentHashMap<>();

    /**
     * Pending 액션 등록 (타임아웃 없음 - 사용자 명시적 응답만 처리)
     */
    public DeferredResult<PendingActionResponse> registerPending(UUID actionId, UUID eventId) {
        DeferredResult<PendingActionResponse> deferredResult = new DeferredResult<>();

        deferredResult.onCompletion(() -> {
            pendingActions.remove(actionId);
            log.info("Pending 액션 완료 및 제거: actionId={}", actionId);
        });

        PendingActionContext context = new PendingActionContext(actionId, eventId, deferredResult);
        pendingActions.put(actionId, context);
        log.info("Pending 액션 등록: actionId={}, eventId={}", actionId, eventId);

        return deferredResult;
    }

    /**
     * Pending 액션 해결 (승인/거부)
     */
    public boolean resolve(UUID actionId, PendingActionResponse response) {
        PendingActionContext context = pendingActions.get(actionId);
        if (context == null) {
            log.warn("Pending 액션을 찾을 수 없음: actionId={}", actionId);
            return false;
        }

        boolean resolved = context.getDeferredResult().setResult(response);
        if (resolved) {
            log.info("Pending 액션 해결: actionId={}, result={}", actionId, response.isResult());
        }
        return resolved;
    }

    /**
     * 특정 액션이 pending 상태인지 확인
     */
    public boolean isPending(UUID actionId) {
        return pendingActions.containsKey(actionId);
    }

    /**
     * 현재 pending 중인 모든 actionId 반환
     */
    public Set<UUID> getPendingActionIds() {
        return pendingActions.keySet();
    }

    /**
     * 특정 이벤트의 pending 액션 조회
     */
    public PendingActionContext getPendingByEventId(UUID eventId) {
        return pendingActions.values().stream()
                .filter(ctx -> ctx.getEventId().equals(eventId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Pending 액션 컨텍스트
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PendingActionContext {
        private UUID actionId;
        private UUID eventId;
        private DeferredResult<PendingActionResponse> deferredResult;
    }
}

