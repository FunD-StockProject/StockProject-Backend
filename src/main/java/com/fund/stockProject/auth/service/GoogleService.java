package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleService {
    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;
    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String tokenUri;
    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String userInfoUri;

    public GoogleTokenResponse getAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", redirectUri);

        GoogleTokenResponse response = webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .bodyToMono(GoogleTokenResponse.class)
                .timeout(Duration.ofSeconds(6))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                    .filter(ex -> ex instanceof java.io.IOException))
                .block();
        // TODO: KakaoTokenResponse를 반환
        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("Failed to retrieve access token from Kakao");
        }

        return response;
    }

    public Map<String, Object> getUserInfo(String accessToken) {
        return webClient.get()
                .uri(userInfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(6))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                    .filter(ex -> ex instanceof java.io.IOException))
                .block();
    }
}
