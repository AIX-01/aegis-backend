package com.aegis.aegisbackend.infra.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 임시 클립 정리 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TempClipCleanupScheduler {

    private final S3Service s3Service;

    /**
     * 매 시간 temp/clips/ 전체 삭제
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupTempClips() {
        log.info("임시 클립 정리 시작");
        s3Service.cleanupTempClips();
    }
}
