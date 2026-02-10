package com.aegis.aegisbackend.domain.manual.controller;

import com.aegis.aegisbackend.domain.manual.dto.ManualDto;
import com.aegis.aegisbackend.domain.manual.service.ManualService;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 매뉴얼 API (관리자 전용)
 * - 매뉴얼 CRUD
 * - Python Agent 임베딩 동기화
 */
@RestController
@RequestMapping("/api/manuals")
@RequiredArgsConstructor
public class ManualController {

    private final ManualService manualService;

    /**
     * 매뉴얼 목록 조회 (페이지네이션)
     */
    @GetMapping
    public ResponseEntity<PageResponse<ManualDto>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(manualService.getManualsPaged(page, size));
    }

    /**
     * 매뉴얼 단건 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ManualDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(manualService.getManualById(id));
    }

    /**
     * 매뉴얼 생성
     */
    @PostMapping
    public ResponseEntity<ManualDto> create(@RequestBody ManualDto.CreateRequest request) {
        return ResponseEntity.ok(manualService.createManual(request));
    }

    /**
     * 매뉴얼 수정
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ManualDto> update(
            @PathVariable UUID id,
            @RequestBody ManualDto.UpdateRequest request) {
        return ResponseEntity.ok(manualService.updateManual(id, request));
    }

    /**
     * 매뉴얼 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        manualService.deleteManual(id);
        return ResponseEntity.noContent().build();
    }
}

