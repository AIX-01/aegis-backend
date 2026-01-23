package com.aegis.aegisbackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 비동기 및 스케줄링 설정
 * - @EnableAsync: 비동기 메서드 실행 지원
 * - @EnableScheduling: @Scheduled 어노테이션 지원
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}

