package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.TokensResponse;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.oauth2.KakaoOAuth2UserInfo;
import com.fund.stockProject.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public TokensResponse kakaoLogin(String code, String state) {
        String redirectUri = decodeState(state);
        String accessToken = kakaoService.getAccessToken(code, redirectUri);
        Map<String, Object> attributes = kakaoService.getUserInfo(accessToken);
        KakaoOAuth2UserInfo kakaoUserInfo = new KakaoOAuth2UserInfo(attributes);


        User user = userRepository.findByEmail(kakaoUserInfo.getEmail()).orElseThrow(
                () -> new NoSuchElementException("해당 이메일로 가입된 사용자가 없습니다.")
        );

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
