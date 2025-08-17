package com.fund.stockProject.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Value("${fcm.credentials.path:classpath:humanzipyo-fcm-service-account.json}")
    private Resource serviceAccount;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        try {
            return FirebaseApp.getInstance();
        } catch (IllegalStateException e) {
            if (serviceAccount == null || !serviceAccount.exists()) {
                throw new IllegalStateException("FCM credentials not found. Set fcm.credentials.path to a valid file or classpath resource.");
            }
            try (var stream = serviceAccount.getInputStream()) {
                var options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(stream))
                        .build();
                return FirebaseApp.initializeApp(options);
            }
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app);
    }
}
