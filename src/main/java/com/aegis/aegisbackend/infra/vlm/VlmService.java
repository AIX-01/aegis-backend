package com.aegis.aegisbackend.infra.vlm;

import com.aegis.aegisbackend.infra.vlm.dto.VlmAnalysisRequest;
import com.aegis.aegisbackend.infra.vlm.dto.VlmAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * VLM(Vision Language Model) 분석 서비스
 * - 8장 프레임 버퍼가 모이면 VLM 분석 요청
 * - 위험 상황 감지 시 Agent 연동
 *
 * TODO: 실제 VLM API 연동 구현 필요
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VlmService {

    @Value("${vlm.api-url:http://localhost:8000}")
    private String vlmApiUrl;

    @Value("${vlm.enabled:false}")
    private boolean vlmEnabled;

    /**
     * 프레임 버퍼 분석 요청
     * - 8장의 프레임을 VLM에 전송하여 분석
     *
     * @param cameraId 카메라 ID
     * @param frames Base64 인코딩된 프레임 이미지 목록 (8장)
     * @return 분석 결과
     */
    public VlmAnalysisResponse analyzeFrames(UUID cameraId, List<String> frames) {
        if (!vlmEnabled) {
            log.debug("VLM 비활성화 상태 - cameraId={}", cameraId);
            return VlmAnalysisResponse.builder()
                    .cameraId(cameraId)
                    .isDangerous(false)
                    .confidence(0.0)
                    .eventType(null)
                    .description("VLM 비활성화 상태")
                    .build();
        }

        log.info("VLM 분석 요청 - cameraId={}, frames={}", cameraId, frames.size());

        // TODO: 실제 VLM API 호출 구현
        // WebClient를 사용하여 VLM 서버에 요청
        // 현재는 스텁 응답 반환

        return VlmAnalysisResponse.builder()
                .cameraId(cameraId)
                .isDangerous(false)
                .confidence(0.0)
                .eventType(null)
                .description("VLM 분석 미구현 - 스텁 응답")
                .build();
    }

    /**
     * VLM 서버 상태 확인
     */
    public boolean isVlmServerHealthy() {
        if (!vlmEnabled) {
            return false;
        }

        // TODO: VLM 서버 헬스체크 구현
        log.debug("VLM 서버 상태 확인 - url={}", vlmApiUrl);
        return false;
    }
}
