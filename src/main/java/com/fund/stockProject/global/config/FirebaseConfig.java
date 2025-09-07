package com.fund.stockProject.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${fcm.credentials.path}")
    private Resource serviceAccount;

    // 반드시 명시 (프로젝트 혼선 차단)
    @Value("${fcm.project-id}")
    private String projectId;

    // 이름 있는 App 으로 강제 초기화 (기존 DefaultApp 재사용 금지)
    private static final String APP_NAME = "stockProject-fcm";

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (serviceAccount == null || !serviceAccount.exists()) {
            throw new IllegalStateException("FCM credentials not found. Set fcm.credentials.path to a valid file or classpath resource.");
        }

        // 이미 동일 이름의 앱이 있으면 그걸 사용 (옵션 일치 여부도 로깅)
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (APP_NAME.equals(app.getName())) {
                log.info("[FCM] Reusing FirebaseApp name={}, project={}", app.getName(), projectId);
                return app;
            }
        }

        GoogleCredentials base = GoogleCredentials.fromStream(serviceAccount.getInputStream())
                .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
        // 토큰 한 번 미리 받아서 인증 경로/시계 확인
        base.refreshIfExpired();

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(base)
                .setProjectId(projectId) // ★ 명시
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options, APP_NAME);

        // 디버그: 실제 사용 중인 서비스계정/프로젝트 로깅
        if (base instanceof ServiceAccountCredentials sac) {
            log.info("[FCM] Initialized app={} svcEmail={} saProjectId={} effectiveProjectId={}",
                    APP_NAME, sac.getClientEmail(), sac.getProjectId(), projectId);
        } else {
            log.info("[FCM] Initialized app={} with non-service-account credentials, effectiveProjectId={}",
                    APP_NAME, projectId);
        }

        return app;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
