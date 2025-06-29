package com.fund.stockProject.global.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

import com.fund.stockProject.global.dto.request.AccessTokenRequest;
import com.fund.stockProject.global.dto.response.AccessTokenResponse;

@Configuration
public class SecurityHttpConfig {

    @Value("${spring.security.appkey}")
    private String appkey;

    @Value("${spring.security.appsecret}")
    private String appSecret;

    private volatile String accessToken;
    private volatile LocalDateTime expiredDateTime = LocalDateTime.now();

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                        .baseUrl("https://openapi.koreainvestment.com:9443")
                        .build(); // 기본 헤더는 WebClient 호출 시 동적으로 설정
    }

    public HttpHeaders createSecurityHeaders() {
        refreshTokenIfNeeded(); // 항상 최신 토큰을 보장
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("appkey", appkey);
        headers.set("appSecret", appSecret);
        headers.set("custtype", "P");
        return headers;
    }

    private String fetchAccessTokenFromApi() {
        WebClient webClient = WebClient.create();
        AccessTokenRequest request = new AccessTokenRequest("client_credentials", appkey, appSecret);

        try {
            AccessTokenResponse response = webClient.post()
                                                    .uri("https://openapi.koreainvestment.com:9443/oauth2/tokenP")
                                                    .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                                                    .bodyValue(request)
                                                    .retrieve()
                                                    .bodyToMono(AccessTokenResponse.class)
                                                    .block();

            if (response != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                expiredDateTime = LocalDateTime.parse(response.getAccessTokenExpired(), formatter);
                return response.getAccessToken();
            } else {
                throw new RuntimeException("Failed to fetch access token: Response is null");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch access token", e);
        }
    }

    public void refreshTokenIfNeeded() {
        if (accessToken == null || LocalDateTime.now().isAfter(expiredDateTime)) {
            synchronized (this) {
                if (accessToken == null || LocalDateTime.now().isAfter(expiredDateTime)) {
                    String oldToken = accessToken;
                    accessToken = fetchAccessTokenFromApi();
                    System.out.println("토큰 기간 만료: " + expiredDateTime);
                    System.out.println("이전 AccessToken: " + oldToken);
                    System.out.println("새로운 AccessToken: " + accessToken);
                }
            }
        }
    }

    @Scheduled(fixedRate = 3600000) // 1시간마다 토큰 갱신 확인
    public void scheduledTokenRefresh() {
        refreshTokenIfNeeded();
    }
}
