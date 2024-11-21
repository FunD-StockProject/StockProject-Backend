package com.fund.stockProject.global.config;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fund.stockProject.global.dto.request.AccessTokenRequest;
import com.fund.stockProject.global.dto.response.AccessTokenResponse;

@Configuration
public class SecurityHttpConfig {

    @Value("${security.appkey}")
    private String appkey;

    @Value("${security.appsecret}")
    private String appSecret;

    private String accessToken; // 초기화를 나중에 수행
    private LocalDateTime lastTokenUpdateTime = LocalDateTime.now(); // 마지막 갱신 시간

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                        .baseUrl("https://openapi.koreainvestment.com:9443")
                        .defaultHeaders(httpHeaders -> httpHeaders.addAll(createSecurityHeaders()))
                        .build();
    }

    @Bean
    public synchronized HttpHeaders createSecurityHeaders() {
        refreshTokenIfNeeded(); // 헤더 생성 시 토큰 갱신 여부 확인
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
                return response.getAccessToken();
            } else {
                throw new RuntimeException("Failed to fetch access token: Response is null");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch access token", e);
        }
    }

    public synchronized void refreshTokenIfNeeded() {
        if (accessToken == null || lastTokenUpdateTime.plusHours(24).isBefore(LocalDateTime.now())) {
            String oldToken = accessToken; // 이전 토큰 로깅
            accessToken = fetchAccessTokenFromApi();
            lastTokenUpdateTime = LocalDateTime.now();
            System.out.println("AccessToken 갱신 완료: " + lastTokenUpdateTime);
            System.out.println("이전 AccessToken: " + oldToken);
            System.out.println("새로운 AccessToken: " + accessToken);
        }
    }
}