package com.fund.stockProject.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class LogoutSuccessHandler implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {

    private final RestTemplate restTemplate; // API 호출용

    // 네이버 클라이언트 ID/Secret은 보안상 직접 하드코딩하지 않고, application.yml에서 주입받는 것이 좋습니다.
    // @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    // private String naverClientId;
    // @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    // private String naverClientSecret;

    public LogoutSuccessHandler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // Spring Security Context에서 인증된 사용자 정보를 가져옵니다.
        // 소셜 계정 연동 해제 로직
//        if (authentication != null && authentication.getPrincipal() instanceof CustomPrincipal) {
//            CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();
//            User user = customPrincipal.getUser(); // CustomPrincipal에서 User 객체를 가져옵니다.
//
//
//            String provider = user.getProvider();
//            String socialAccessToken = user.getSocialAccessToken();
//
//            if (provider != null && socialAccessToken != null) {
//                switch (provider) {
//                    case "kakao":
//                        logoutKakao(socialAccessToken);
//                        break;
//                    case "google":
//                        logoutGoogle(socialAccessToken);
//                        break;
//                    case "naver":
//                        // 네이버는 client_id와 client_secret이 필요합니다.
//                        // 실제 값을 주입받아 사용하세요.
//                        logoutNaver(socialAccessToken, "OZwth1dkwDSJ1Iq5kBhZ", "hGleYdyxuC");
//                        break;
//                    // 다른 소셜 로그인 제공자 추가
//                    default:
//                        // 이 서비스는 소셜 로그아웃 API가 없거나 필요하지 않음
//                        break;
//                }
//            }
//        }

        // 모든 로그아웃 처리가 완료된 후, 프론트엔드로 리다이렉트합니다.
        // 이 URL은 여러분의 프론트엔드 로그인 페이지나 메인 페이지여야 합니다.
        response.setStatus(HttpServletResponse.SC_OK); // 200 OK
        response.setContentType("application/json;charset=UTF-8"); // JSON 응답 명시
        response.getWriter().write("{\"message\": \"Logged out successfully\"}");
        response.getWriter().flush();
    }

    // --- 각 소셜 서비스별 로그아웃 API 호출 메서드 ---

    private void logoutKakao(String accessToken) {
        String logoutUrl = "https://kapi.kakao.com/v1/user/logout";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(logoutUrl, HttpMethod.POST, entity, String.class);
            System.out.println("[Social Logout] Kakao logout successful.");
        } catch (Exception e) {
            System.err.println("[Social Logout] Failed to logout from Kakao: " + e.getMessage());
        }
    }

    private void logoutGoogle(String accessToken) {
        // 구글 토큰 무효화 (Revoke) 엔드포인트
        String revokeUrl = UriComponentsBuilder.fromUriString("https://oauth2.googleapis.com/revoke")
                .queryParam("token", accessToken)
                .build().toUriString();

        try {
            // 구글은 GET 요청으로도 토큰 무효화 가능
            restTemplate.getForEntity(revokeUrl, String.class);
            System.out.println("[Social Logout] Google logout successful.");
        } catch (Exception e) {
            System.err.println("[Social Logout] Failed to logout from Google: " + e.getMessage());
        }
    }

    private void logoutNaver(String accessToken, String clientId, String clientSecret) {
        // 네이버 토큰 삭제 API
        String logoutUrl = UriComponentsBuilder.fromUriString("https://nid.naver.com/oauth2.0/token")
                .queryParam("grant_type", "delete")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("access_token", accessToken)
                .queryParam("service_provider", "NAVER")
                .build().toUriString();

        try {
            restTemplate.getForEntity(logoutUrl, String.class);
            System.out.println("[Social Logout] Naver logout successful.");
        } catch (Exception e) {
            System.err.println("[Social Logout] Failed to logout from Naver: " + e.getMessage());
        }
    }
}