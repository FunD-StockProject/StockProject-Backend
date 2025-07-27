package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.KakaoTokenResponse;
import com.fund.stockProject.auth.dto.NaverTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NaverService {
    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String clientSecret;
    @Value("${spring.security.oauth2.client.provider.naver.token-uri}")
    private String tokenUri;
    @Value("${spring.security.oauth2.client.provider.naver.user-info-uri}")
    private String userInfoUri;

    public NaverTokenResponse getAccessToken(String code, String redirectUri) throws UnsupportedEncodingException {

        String state = URLEncoder.encode(redirectUri, "UTF-8");

        String fullUrl = tokenUri
                + "?grant_type=authorization_code"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&code=" + code
                + "&state=" + state;

        NaverTokenResponse response = webClient.get()
                .uri(fullUrl)
                .retrieve()
                .bodyToMono(NaverTokenResponse.class)
                .block();

        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("Failed to retrieve access token from Naver");
        }

        return response;
    }

    public Map<String, Object> getUserInfo(String accessToken) {
        String result = webClient.get()
                .uri(userInfoUri)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("Naver userInfo API result: " + result);

        return webClient.get()
                .uri(userInfoUri)
                .headers(headers -> {
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }
}