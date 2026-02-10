package com.aegis.aegisbackend.domain.manual.service;

import com.aegis.aegisbackend.domain.manual.dto.ManualDto;
import com.aegis.aegisbackend.domain.manual.entity.Manual;
import com.aegis.aegisbackend.domain.manual.repository.ManualRepository;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * 매뉴얼 서비스
 * - 매뉴얼 CRUD
 * - Python Agent 임베딩 API 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualService {

    private final ManualRepository manualRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${agent.api.url:http://localhost:8001}")
    private String agentApiUrl;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 매뉴얼 목록 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public PageResponse<ManualDto> getManualsPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<Manual> manualPage = manualRepository.findAllPaged(pageable);
        return PageResponse.from(manualPage, ManualDto::from);
    }

    /**
     * 매뉴얼 단건 조회
     */
    @Transactional(readOnly = true)
    public ManualDto getManualById(UUID manualId) {
        Manual manual = manualRepository.findById(manualId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MANUAL_NOT_FOUND));
        return ManualDto.from(manual);
    }

    /**
     * 매뉴얼 생성
     */
    @Transactional
    public ManualDto createManual(ManualDto.CreateRequest request) {
        Manual manual = Manual.builder()
                .name(request.getName())
                .content(request.getContent())
                .enabled(false)
                .build();

        manualRepository.save(manual);
        log.info("매뉴얼 생성: {}", manual.getName());

        return ManualDto.from(manual);
    }

    /**
     * 매뉴얼 수정
     */
    @Transactional
    public ManualDto updateManual(UUID manualId, ManualDto.UpdateRequest request) {
        Manual manual = manualRepository.findById(manualId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MANUAL_NOT_FOUND));

        boolean enabledChanged = false;
        boolean contentChanged = false;

        if (request.getName() != null) {
            manual.setName(request.getName());
        }
        if (request.getContent() != null && !request.getContent().equals(manual.getContent())) {
            manual.setContent(request.getContent());
            contentChanged = true;
        }
        if (request.getEnabled() != null && !request.getEnabled().equals(manual.getEnabled())) {
            manual.setEnabled(request.getEnabled());
            enabledChanged = true;
        }

        manualRepository.save(manual);
        log.info("매뉴얼 수정: {}", manualId);

        // 내용 변경 또는 enabled 상태 변경 시 임베딩 동기화
        if (contentChanged || enabledChanged) {
            syncEmbeddingToAgent(manual, "update");
        }

        return ManualDto.from(manual);
    }

    /**
     * 매뉴얼 삭제
     */
    @Transactional
    public void deleteManual(UUID manualId) {
        Manual manual = manualRepository.findById(manualId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MANUAL_NOT_FOUND));

        // 임베딩 삭제 요청
        syncEmbeddingToAgent(manual, "delete");

        manualRepository.delete(manual);
        log.info("매뉴얼 삭제: {}", manualId);
    }

    /**
     * Python Agent 임베딩 API 호출
     */
    private void syncEmbeddingToAgent(Manual manual, String action) {
        ManualDto.EmbeddingRequest request = ManualDto.EmbeddingRequest.builder()
                .action(action)
                .manual(ManualDto.EmbeddingRequest.ManualData.builder()
                        .id(manual.getId().toString())
                        .name(manual.getName())
                        .content(manual.getContent())
                        .enabled(manual.getEnabled())
                        .build())
                .build();

        try {
            webClientBuilder.build()
                    .post()
                    .uri(agentApiUrl + "/api/manuals/embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            response -> log.info("임베딩 동기화 성공: {} - {}", action, manual.getId()),
                            error -> log.error("임베딩 동기화 실패: {} - {}", action, manual.getId(), error)
                    );
        } catch (Exception e) {
            log.error("임베딩 동기화 요청 실패: {}", e.getMessage());
        }
    }
}

