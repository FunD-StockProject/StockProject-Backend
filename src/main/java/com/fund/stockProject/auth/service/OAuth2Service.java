package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.auth.oauth2.AppleOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.GoogleOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.KakaoOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.NaverOAuth2UserInfo;
import com.fund.stockProject.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuth2Service {
    private final KakaoService kakaoService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final NaverService naverService;
    private final GoogleService googleService;
    private final AppleService appleService;
    private final ObjectMapper objectMapper;
    private final AppleLoginContextService appleLoginContextService;

    public LoginResponse kakaoLogin(String code, String state) {
        String redirectUri = decodeState(state);
        KakaoTokenResponse response = kakaoService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = kakaoService.getUserInfo(response.getAccessToken());
        KakaoOAuth2UserInfo kakaoUserInfo = new KakaoOAuth2UserInfo(attributes);
        return loginByProvider(
                PROVIDER.KAKAO,
                kakaoUserInfo.getProviderId(),
                kakaoUserInfo.getEmail(),
                response.getAccessToken(),
                response.getRefreshToken()
        );
    }

    public LoginResponse naverLogin(String code, String state) throws UnsupportedEncodingException {
        String redirectUri = decodeState(state);
        NaverTokenResponse response = naverService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = naverService.getUserInfo(response.getAccessToken());
        NaverOAuth2UserInfo naverUserInfo = new NaverOAuth2UserInfo(attributes);
        return loginByProvider(
                PROVIDER.NAVER,
                naverUserInfo.getProviderId(),
                naverUserInfo.getEmail(),
                response.getAccessToken(),
                response.getRefreshToken()
        );
    }

    public LoginResponse googleLogin(String code, String state) {
        String redirectUri = decodeState(state);
        GoogleTokenResponse response = googleService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = googleService.getUserInfo(response.getAccessToken());
        GoogleOAuth2UserInfo googleUserInfo = new GoogleOAuth2UserInfo(attributes);
        return loginByProvider(
                PROVIDER.GOOGLE,
                googleUserInfo.getProviderId(),
                googleUserInfo.getEmail(),
                response.getAccessToken(),
                response.getRefreshToken()
        );
    }

    public LoginResponse appleLogin(String code, String state) {
        return appleLogin(code, state, null, null);
    }

    public LoginResponse appleLogin(String code, String state, String userJson) {
        return appleLogin(code, state, userJson, null);
    }

    public LoginResponse appleLogin(String code, String state, String userJson, String clientKey) {
        String redirectUri = decodeState(state);
        // 1. 애플로 code + client_secret을 보내서 토큰(및 id_token) 받기
        AppleTokenResponse response = appleService.getAccessToken(code, redirectUri);
        // 2. id_token(JWT)에서 사용자 정보 추출 (이메일, sub=providerId 등)
        AppleOAuth2UserInfo appleUserInfo = appleService.getUserInfoFromIdToken(response.getIdToken());

        String providerId = normalizeToNull(appleUserInfo.getProviderId());
        if (providerId == null) {
            throw new IllegalStateException("Apple providerId is missing");
        }

        Optional<User> userByProviderId = userRepository.findByProviderAndProviderId(PROVIDER.APPLE, providerId);
        if (userByProviderId.isPresent()) {
            User user = userByProviderId.get();
            user.updateSocialUserInfo(PROVIDER.APPLE, providerId, response.getAccessToken(), response.getRefreshToken());
            userRepository.save(user);
            return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
        }

        String email = resolveAppleEmail(appleUserInfo.getEmail(), userJson);
        if (email == null) {
            if (clientKey != null && !clientKey.isBlank()) {
                appleLoginContextService.savePendingProviderIdByClient(PROVIDER.APPLE, clientKey, providerId);
            }
            return new LoginResponse("NEED_REGISTER", null, null, null, null, null);
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            appleLoginContextService.savePendingProviderId(PROVIDER.APPLE, email, providerId);
            return new LoginResponse("NEED_REGISTER", email, null, null, null, null);
        }

        User user = userOptional.get();
        user.updateSocialUserInfo(PROVIDER.APPLE, providerId, response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user);

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    private LoginResponse loginByProvider(
            PROVIDER provider,
            String providerId,
            String email,
            String socialAccessToken,
            String socialRefreshToken
    ) {
        String normalizedProviderId = normalizeToNull(providerId);
        if (normalizedProviderId == null) {
            throw new IllegalStateException(provider + " providerId is missing");
        }

        Optional<User> userByProviderId = userRepository.findByProviderAndProviderId(provider, normalizedProviderId);
        if (userByProviderId.isPresent()) {
            User user = userByProviderId.get();
            user.updateSocialUserInfo(provider, normalizedProviderId, socialAccessToken, socialRefreshToken);
            userRepository.save(user);
            return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
        }

        String normalizedEmail = normalizeToNull(email);
        if (normalizedEmail == null) {
            return new LoginResponse("NEED_REGISTER", null, null, null, null, null);
        }

        Optional<User> userByEmail = userRepository.findByEmail(normalizedEmail);
        if (userByEmail.isEmpty()) {
            appleLoginContextService.savePendingProviderId(provider, normalizedEmail, normalizedProviderId);
            return new LoginResponse("NEED_REGISTER", normalizedEmail, null, null, null, null);
        }

        User user = userByEmail.get();
        user.updateSocialUserInfo(provider, normalizedProviderId, socialAccessToken, socialRefreshToken);
        userRepository.save(user);
        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }


    private String resolveAppleEmail(String idTokenEmail, String userJson) {
        String email = normalizeToNull(idTokenEmail);
        if (email != null) {
            return email;
        }
        if (userJson == null || userJson.isBlank()) {
            return null;
        }
        return extractAppleEmail(userJson)
                .map(this::normalizeToNull)
                .orElse(null);
    }

    private Optional<String> extractAppleEmail(String userJson) {
        try {
            JsonNode root = objectMapper.readTree(userJson);
            JsonNode emailNode = root.get("email");
            if (emailNode == null || emailNode.isNull()) {
                return Optional.empty();
            }
            String email = emailNode.asText();
            return email == null || email.isBlank() ? Optional.empty() : Optional.of(email);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }


    private String decodeState(String encodedState) {
        try {
            // URL-Safe Base64 디코더 사용
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedState);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 state 파라미터입니다.");
        }
    }
}
