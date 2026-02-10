package com.aegis.aegisbackend.domain.action.controller;

import com.aegis.aegisbackend.domain.action.dto.ActionDto;
import com.aegis.aegisbackend.domain.action.service.ActionService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 액션 API (관리자 전용)
 * - 액션 CRUD
 * - Redis를 통한 Python Agent 동기화
 */
@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    /**
     * 액션 목록 조회 (페이지네이션)
     */
    @GetMapping
    public ResponseEntity<PageResponse<ActionDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(actionService.getActionsPaged(page, size));
    }

    /**
     * 액션 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ActionDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(actionService.getActionById(id));
    }

    /**
     * 액션 생성
     */
    @PostMapping
    public ResponseEntity<ActionDto> create(@RequestBody ActionDto.CreateRequest request) {
        return ResponseEntity.ok(actionService.createAction(request));
    }

    /**
     * 액션 수정
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ActionDto> update(
            @PathVariable UUID id,
            @RequestBody ActionDto.UpdateRequest request) {
        return ResponseEntity.ok(actionService.updateAction(id, request));
    }

    /**
     * 액션 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        actionService.deleteAction(id);
        return ResponseEntity.noContent().build();
    }
}

