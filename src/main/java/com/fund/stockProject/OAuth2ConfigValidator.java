package com.fund.stockProject;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OAuth2ConfigValidator {

    @Value("${security.oauth2.client.registration.naver.client-id:NOT_FOUND}")
    private String naverClientId;

    @Value("${security.oauth2.client.registration.kakao.client-id:NOT_FOUND}")
    private String kakaoClientId;

    @PostConstruct
    public void validateConfig() {
        log.info("Naver Client ID loaded: {}",
                naverClientId.equals("NOT_FOUND") ? "FAILED" : "SUCCESS");
        log.info("Kakao Client ID loaded: {}",
                kakaoClientId.equals("NOT_FOUND") ? "FAILED" : "SUCCESS");
    }
}
