package com.fund.stockProject.global.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import com.fund.stockProject.global.config.SecurityHttpConfig;

@Component
public class AccessTokenScheduler {

    private final SecurityHttpConfig securityHttpConfig;

    public AccessTokenScheduler(SecurityHttpConfig securityHttpConfig) {
        this.securityHttpConfig = securityHttpConfig;
    }

    @Scheduled(fixedRate = 86400100) // 24시간 10초 (밀리초 단위: 86400000 + 10000)
    public void refreshAccessToken() {
        securityHttpConfig.refreshTokenIfNeeded();
        System.out.println("AccessToken 갱신 완료: " + LocalDateTime.now());
    }
}