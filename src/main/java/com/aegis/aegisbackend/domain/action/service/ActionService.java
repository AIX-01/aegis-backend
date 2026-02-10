package com.aegis.aegisbackend.domain.action.service;

import com.aegis.aegisbackend.domain.action.dto.ActionDto;
import com.aegis.aegisbackend.domain.action.entity.Action;
import com.aegis.aegisbackend.domain.action.repository.ActionRepository;
import com.aegis.aegisbackend.global.common.dto.PageResponse;
import com.aegis.aegisbackend.global.exception.BusinessException;
import com.aegis.aegisbackend.global.exception.ErrorCode;
import com.aegis.aegisbackend.infra.redis.RedisTokenService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 액션 서비스
 * - 액션 CRUD
 * - Redis 동기화 (enabled=true인 것만)
 * - 기본 액션 초기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionService {

    private final ActionRepository actionRepository;
    private final RedisTokenService redisTokenService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 앱 시작 시 기본 액션 초기화
     */
    @PostConstruct
    public void initDefaultActions() {
        if (actionRepository.count() == 0) {
            log.info("기본 액션 초기화 시작...");
            createDefaultActions();
            log.info("기본 액션 초기화 완료");
        }
    }

    /**
     * 액션 목록 조회 (페이지네이션)
     */
    @Transactional(readOnly = true)
    public PageResponse<ActionDto> getActionsPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size > 0 ? size : DEFAULT_PAGE_SIZE);
        Page<Action> actionPage = actionRepository.findAllPaged(pageable);
        return PageResponse.from(actionPage, ActionDto::from);
    }

    /**
     * 액션 단건 조회
     */
    @Transactional(readOnly = true)
    public ActionDto getActionById(UUID actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_NOT_FOUND));
        return ActionDto.from(action);
    }

    /**
     * 액션 생성
     */
    @Transactional
    public ActionDto createAction(ActionDto.CreateRequest request) {
        Action action = Action.builder()
                .name(request.getName())
                .description(request.getDescription())
                .parameters(request.getParameters())
                .code(request.getCode())
                .enabled(false)
                .build();

        actionRepository.save(action);
        log.info("액션 생성: {}", action.getName());

        return ActionDto.from(action);
    }

    /**
     * 액션 수정
     */
    @Transactional
    public ActionDto updateAction(UUID actionId, ActionDto.UpdateRequest request) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_NOT_FOUND));

        boolean enabledChanged = false;

        if (request.getName() != null) {
            action.setName(request.getName());
        }
        if (request.getDescription() != null) {
            action.setDescription(request.getDescription());
        }
        if (request.getParameters() != null) {
            action.setParameters(request.getParameters());
        }
        if (request.getCode() != null) {
            action.setCode(request.getCode());
        }
        if (request.getEnabled() != null && !request.getEnabled().equals(action.getEnabled())) {
            action.setEnabled(request.getEnabled());
            enabledChanged = true;
        }

        actionRepository.save(action);
        log.info("액션 수정: {}", actionId);

        // enabled 상태 변경 시 Redis 동기화
        if (enabledChanged) {
            syncActionsToRedis();
        }

        return ActionDto.from(action);
    }

    /**
     * 액션 삭제
     */
    @Transactional
    public void deleteAction(UUID actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_NOT_FOUND));

        boolean wasEnabled = action.getEnabled();
        actionRepository.delete(action);
        log.info("액션 삭제: {}", actionId);

        // 활성화된 액션이었다면 Redis 동기화
        if (wasEnabled) {
            syncActionsToRedis();
        }
    }

    /**
     * enabled=true인 액션 목록을 Redis에 저장하고 Pub/Sub 알림 발행
     */
    public void syncActionsToRedis() {
        List<Action> enabledActions = actionRepository.findByEnabledTrue();

        List<Map<String, Object>> actionList = enabledActions.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId().toString(),
                        "name", a.getName(),
                        "description", a.getDescription(),
                        "parameters", a.getParameters() != null ? a.getParameters() : Map.of(),
                        "code", a.getCode()
                ))
                .toList();

        try {
            String json = objectMapper.writeValueAsString(actionList);
            redisTokenService.saveActionsAndNotify(json);
        } catch (JsonProcessingException e) {
            log.error("액션 목록 JSON 변환 실패", e);
        }
    }

    /**
     * 기본 액션 생성
     */
    private void createDefaultActions() {
        // 메일 발송
        actionRepository.save(Action.builder()
                .name("메일 발송")
                .description("이메일 알림 발송")
                .parameters(Map.of(
                        "to_email", Map.of("type", "str", "description", "수신자 이메일", "default_value", ""),
                        "subject", Map.of("type", "str", "description", "메일 제목", "default_value", ""),
                        "body", Map.of("type", "str", "description", "메일 본문", "default_value", "")
                ))
                .code("""
                        import smtplib
                        from email.mime.text import MIMEText
                        
                        def execute(to_email: str, subject: str, body: str) -> str:
                            # SMTP 설정 필요
                            msg = MIMEText(body)
                            msg['Subject'] = subject
                            msg['To'] = to_email
                            # 실제 발송 로직 구현 필요
                            return f"메일 발송 완료: {to_email}"
                        """)
                .enabled(false)
                .build());

        // 문자 발송
        actionRepository.save(Action.builder()
                .name("문자 발송")
                .description("SMS 알림 발송")
                .parameters(Map.of(
                        "phone_number", Map.of("type", "str", "description", "수신자 전화번호", "default_value", ""),
                        "message", Map.of("type", "str", "description", "메시지 내용", "default_value", "")
                ))
                .code("""
                        def execute(phone_number: str, message: str) -> str:
                            # SMS API 연동 필요
                            return f"문자 발송 완료: {phone_number}"
                        """)
                .enabled(false)
                .build());

        // 웹훅 호출
        actionRepository.save(Action.builder()
                .name("웹훅 호출")
                .description("외부 Webhook 호출")
                .parameters(Map.of(
                        "url", Map.of("type", "str", "description", "웹훅 URL", "default_value", ""),
                        "method", Map.of("type", "str", "description", "HTTP 메서드 (GET/POST)", "default_value", "POST"),
                        "body", Map.of("type", "str", "description", "요청 본문 (JSON)", "default_value", "")
                ))
                .code("""
                        import requests
                        import json
                        
                        def execute(url: str, method: str, body: str) -> str:
                            headers = {"Content-Type": "application/json"}
                            if method.upper() == "GET":
                                response = requests.get(url, headers=headers)
                            else:
                                response = requests.post(url, headers=headers, data=body)
                            return f"웹훅 호출 완료: {response.status_code}"
                        """)
                .enabled(false)
                .build());

        // 112 신고
        actionRepository.save(Action.builder()
                .name("112 신고")
                .description("경찰 신고")
                .parameters(Map.of(
                        "location", Map.of("type", "str", "description", "사건 발생 위치", "default_value", ""),
                        "description", Map.of("type", "str", "description", "상황 설명", "default_value", "")
                ))
                .code("""
                        def execute(location: str, description: str) -> str:
                            # 112 신고 API 연동 필요
                            return f"112 신고 접수: {location}"
                        """)
                .enabled(false)
                .build());

        // 119 신고
        actionRepository.save(Action.builder()
                .name("119 신고")
                .description("소방/응급 신고")
                .parameters(Map.of(
                        "location", Map.of("type", "str", "description", "사건 발생 위치", "default_value", ""),
                        "description", Map.of("type", "str", "description", "상황 설명", "default_value", "")
                ))
                .code("""
                        def execute(location: str, description: str) -> str:
                            # 119 신고 API 연동 필요
                            return f"119 신고 접수: {location}"
                        """)
                .enabled(false)
                .build());
    }
}

