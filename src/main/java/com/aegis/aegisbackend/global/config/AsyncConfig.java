package com.aegis.aegisbackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 설정
 * - @EnableAsync: 비동기 메서드 실행 지원
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

