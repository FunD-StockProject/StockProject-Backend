package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.GoogleTokenResponse;
import com.fund.stockProject.auth.dto.KakaoTokenResponse;
import com.fund.stockProject.auth.dto.NaverTokenResponse;
import com.fund.stockProject.auth.dto.TokensResponse;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.oauth2.GoogleOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.KakaoOAuth2UserInfo;
import com.fund.stockProject.auth.oauth2.NaverOAuth2UserInfo;
import com.fund.stockProject.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class OAuth2Service {
    private final KakaoService kakaoService;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final NaverService naverService;
    private final GoogleService googleService;

    public TokensResponse kakaoLogin(String code, String state) {
        String redirectUri = decodeState(state);
        KakaoTokenResponse response = kakaoService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = kakaoService.getUserInfo(response.getAccessToken());
        KakaoOAuth2UserInfo kakaoUserInfo = new KakaoOAuth2UserInfo(attributes);

        User user = userRepository.findByEmail(kakaoUserInfo.getEmail()).orElseThrow(
                () -> new NoSuchElementException("해당 이메일로 가입된 사용자가 없습니다.")
        );

        user.updateSocialUserInfo(PROVIDER.KAKAO, kakaoUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트
        // TODO: 소셜 사용자 정보 업데이트 로직 추가

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    public TokensResponse naverLogin(String code, String state) throws UnsupportedEncodingException {
        String redirectUri = decodeState(state);
        NaverTokenResponse response = naverService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = naverService.getUserInfo(response.getAccessToken());
        NaverOAuth2UserInfo naverUserInfo = new NaverOAuth2UserInfo(attributes);

        User user = userRepository.findByEmail(naverUserInfo.getEmail()).orElseThrow(
                () -> new NoSuchElementException("해당 이메일로 가입된 사용자가 없습니다.")
        );

        user.updateSocialUserInfo(PROVIDER.NAVER, naverUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트
        // TODO: 소셜 사용자 정보 업데이트 로직 추가

        return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);
    }

    public TokensResponse googleLogin(String code, String state) {
        String redirectUri = decodeState(state);
        GoogleTokenResponse response = googleService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = googleService.getUserInfo(response.getAccessToken());
        GoogleOAuth2UserInfo googleUserInfo = new GoogleOAuth2UserInfo(attributes);

        User user = userRepository.findByEmail(googleUserInfo.getEmail()).orElseThrow(
                () -> new NoSuchElementException("해당 이메일로 가입된 사용자가 없습니다.")
        );

        user.updateSocialUserInfo(PROVIDER.GOOGLE, googleUserInfo.getProviderId(), response.getAccessToken(), response.getRefreshToken());
        userRepository.save(user); // 사용자 정보 업데이트
        // TODO: 소셜 사용자 정보 업데이트 로직 추가

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
