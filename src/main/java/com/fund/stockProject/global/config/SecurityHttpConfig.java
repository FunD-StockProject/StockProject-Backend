package com.fund.stockProject.global.config;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

import jakarta.annotation.PostConstruct;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

import com.fund.stockProject.global.dto.request.AccessTokenRequest;
import com.fund.stockProject.global.dto.response.AccessTokenResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

@Slf4j
@Configuration
public class SecurityHttpConfig {
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String KIS_BASE_URL = "https://openapi.koreainvestment.com:9443";

    @Value("${spring.security.appkey}")
    private String appkey;

    @Value("${spring.security.appsecret}")
    private String appSecret;

    private volatile String accessToken;
    private volatile LocalDateTime expiredDateTime = LocalDateTime.now();

    @PostConstruct
    public void warmUpAccessToken() {
        try {
            refreshTokenIfNeeded();
        } catch (Exception e) {
            log.warn("Access token warm-up failed. It will retry on demand. error: {}", e.getMessage());
        }
    }

    @Bean
    public WebClient webClient() {
        return buildKisWebClient(); // 기본 헤더는 WebClient 호출 시 동적으로 설정
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
        log.info("Fetching access token from Korea Investment API");
        final WebClient webClient = buildKisWebClient();
        AccessTokenRequest request = new AccessTokenRequest("client_credentials", appkey, appSecret);

        try {
            AccessTokenResponse response = webClient.post()
                                                    .uri("/oauth2/tokenP")
                                                    .headers(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                                                    .bodyValue(request)
                                                    .retrieve()
                                                    .bodyToMono(AccessTokenResponse.class)
                                                    .timeout(RESPONSE_TIMEOUT)
                                                    .block(RESPONSE_TIMEOUT.plusSeconds(1));

            if (response != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                expiredDateTime = LocalDateTime.parse(response.getAccessTokenExpired(), formatter);
                log.info("Access token fetched successfully - expires at: {}", expiredDateTime);
                return response.getAccessToken();
            } else {
                log.error("Failed to fetch access token: Response is null");
                throw new RuntimeException("Failed to fetch access token: Response is null");
            }

        } catch (Exception e) {
            log.error("Failed to fetch access token from Korea Investment API", e);
            throw new RuntimeException("Failed to fetch access token", e);
        }
    }

    private WebClient buildKisWebClient() {
        final HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .responseTimeout(RESPONSE_TIMEOUT);

        return WebClient.builder()
            .baseUrl(KIS_BASE_URL)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    public void refreshTokenIfNeeded() {
        if (accessToken == null || LocalDateTime.now().isAfter(expiredDateTime)) {
            synchronized (this) {
                if (accessToken == null || LocalDateTime.now().isAfter(expiredDateTime)) {
                    String oldToken = accessToken;
                    log.info("Token expired or null - refreshing. Expired at: {}, Old token: {}", expiredDateTime, oldToken != null ? "exists" : "null");
                    accessToken = fetchAccessTokenFromApi();
                    log.info("Token refreshed successfully");
                }
            }
        }
    }

    @Scheduled(fixedRate = 3600000) // 1시간마다 토큰 갱신 확인
    public void scheduledTokenRefresh() {
        refreshTokenIfNeeded();
    }
}
