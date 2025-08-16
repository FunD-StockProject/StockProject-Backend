package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.auth.oauth2.AppleOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.GoogleOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.KakaoOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.NaverOAuth2UserInfo;
import com.fund.stockProject.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;
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

    public LoginResponse kakaoLogin(String code, String state) {
        String redirectUri = decodeState(state);
        KakaoTokenResponse response = kakaoService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = kakaoService.getUserInfo(response.getAccessToken());
        KakaoOAuth2UserInfo kakaoUserInfo = new KakaoOAuth2UserInfo(attributes);

        Optional<User> userOptional = userRepository.findByEmail(kakaoUserInfo.getEmail());
        if(userOptional.isEmpty()) {
            return new LoginResponse("NEED_REGISTER", kakaoUserInfo.getEmail(), null, null, null);
        }

        User user = userOptional.get();
        user.updateSocialUserInfo(PROVIDER.KAKAO, kakaoUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    public LoginResponse naverLogin(String code, String state) throws UnsupportedEncodingException {
        String redirectUri = decodeState(state);
        NaverTokenResponse response = naverService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = naverService.getUserInfo(response.getAccessToken());
        NaverOAuth2UserInfo naverUserInfo = new NaverOAuth2UserInfo(attributes);

        Optional<User> userOptional = userRepository.findByEmail(naverUserInfo.getEmail());
        if(userOptional.isEmpty()) {
            return new LoginResponse("NEED_REGISTER", naverUserInfo.getEmail(), null, null, null);
        }

        User user = userOptional.get();
        user.updateSocialUserInfo(PROVIDER.NAVER, naverUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    public LoginResponse googleLogin(String code, String state) {
        String redirectUri = decodeState(state);
        GoogleTokenResponse response = googleService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = googleService.getUserInfo(response.getAccessToken());
        GoogleOAuth2UserInfo googleUserInfo = new GoogleOAuth2UserInfo(attributes);

        Optional<User> userOptional = userRepository.findByEmail(googleUserInfo.getEmail());
        if(userOptional.isEmpty()) {
            return new LoginResponse("NEED_REGISTER", googleUserInfo.getEmail(), null, null, null);
        }

        User user = userOptional.get();
        user.updateSocialUserInfo(PROVIDER.GOOGLE, googleUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    public LoginResponse appleLogin(String code, String state) {
        String redirectUri = decodeState(state);
        // 1. 애플로 code + client_secret을 보내서 토큰(및 id_token) 받기
        AppleTokenResponse response = appleService.getAccessToken(code, redirectUri);
        // 2. id_token(JWT)에서 사용자 정보 추출 (이메일, sub=providerId 등)
        AppleOAuth2UserInfo appleUserInfo = appleService.getUserInfoFromIdToken(response.getIdToken());

        Optional<User> userOptional = userRepository.findByEmail(appleUserInfo.getEmail());
        if (userOptional.isEmpty()) {
            return new LoginResponse("NEED_REGISTER", appleUserInfo.getEmail(), null, null, null);
        }

        User user = userOptional.get();
        user.updateSocialUserInfo(PROVIDER.APPLE, appleUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user);

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
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
