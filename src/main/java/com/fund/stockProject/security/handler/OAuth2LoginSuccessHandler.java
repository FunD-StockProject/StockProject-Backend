package com.fund.stockProject.security.handler;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.auth.service.TokenService;
import com.fund.stockProject.global.config.DomainConfig;
import com.fund.stockProject.security.principle.CustomPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Iterator;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final OAuth2AuthorizedClientService authorizedClientService; // OAuth2AuthorizedClientService는 OAuth2 로그인 성공 후 사용자 정보를 가져오는 데 사용됩니다.
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final DomainConfig domainConfig;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId(); // "kakao", "naver", "google"
        String email = oauthToken.getName(); // OAuth2 로그인 시 이메일은 사용자 이름으로 사용됩니다.

        Collection<? extends GrantedAuthority> authorities = oauthToken.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority authority = iterator.next();
        String role = authority.getAuthority();

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(provider, email);

        if (authorizedClient == null) {
            // 여기도 적절한 처리 필요
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authorize Client Not Found");
            return;
        }



        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken(); // ⭐ Refresh Token!

        String socialAccessTokenValue = accessToken.getTokenValue();
        String socialRefreshTokenValue = (refreshToken != null) ? refreshToken.getTokenValue() : null;
        LocalDateTime accessTokenExpiresAt = (accessToken.getExpiresAt() != null) ?
                LocalDateTime.ofInstant(accessToken.getExpiresAt(), ZoneOffset.systemDefault()) : null;
        // *****



        // 1. Access Token 및 Refresh Token 발급 및 쿠키에 설정
        // 이전에 수정한 TokenService.issueTokensOnLogin() 메서드가 이미
        // Access Token과 Refresh Token을 HttpOnly, Secure, SameSite 쿠키로
        // 응답 헤더에 추가하도록 구현되어 있습니다.



        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));
        user.updateSocialUserInfo(socialAccessTokenValue, socialRefreshTokenValue, accessTokenExpiresAt);
        userRepository.save(user); // 사용자 정보 업데이트

        // 2. 리다이렉트 URL 설정
        // OAuth2 로그인 성공 후 프론트엔드로 리다이렉트하여
        // 브라우저가 Set-Cookie 헤더를 통해 받은 쿠키들을 저장하게 합니다.
        String targetUrl;
        if (customPrincipal.getIsNewUser()) {
            tokenService.issueTempTokenForNewUser(response, email, role);
            // 새로운 사용자일 경우 회원가입 완료 페이지 등으로 리다이렉트
            // TODO: 실제 회원가입 완료 페이지 URL로 변경 필요
            targetUrl = UriComponentsBuilder.fromUriString(domainConfig.getProd() + "/auth/join")
                    .queryParam("email", URLEncoder.encode(customPrincipal.getUserEmail(), StandardCharsets.UTF_8))
                    .build().toUriString();
        } else {
            // 기존 사용자일 경우 메인 페이지 또는 로그인 완료 페이지 등으로 리다이렉트
            tokenService.issueTokensOnLogin(response, null, email, role);
            // TODO: 실제 메인 페이지 URL로 변경 필요
            targetUrl = UriComponentsBuilder.fromUriString(domainConfig.getProd() + "/mypage")
                    .build().toUriString();
        }

        response.sendRedirect(targetUrl);
    }
}