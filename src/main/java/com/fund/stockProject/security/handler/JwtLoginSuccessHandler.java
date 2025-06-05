package com.fund.stockProject.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.service.TokenService;
import com.fund.stockProject.security.principle.CustomPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();
        String email = customPrincipal.getUsername();

        Collection<? extends GrantedAuthority> authorities = customPrincipal.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority authority = iterator.next();
        String role = authority.getAuthority();

        // 1. Access Token 및 Refresh Token 발급 및 쿠키에 설정
        // issueTokensOnLogin 메서드는 이제 토큰을 반환하지 않고, 직접 쿠키에 설정합니다.
        tokenService.issueTokensOnLogin(response, null, email, role); // 기존 리프레시 토큰은 로그인 시 null로 전달

        // 2. 응답 설정
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // 3. 응답 본문에 필요한 데이터 (메시지 등)를 JSON 형태로 작성
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Login successful");
        // Access Token은 이제 쿠키에 있으므로 응답 본문에서 제거합니다.
        // responseBody.put("accessToken", accessToken);

        // 4. JSON 응답 본문을 클라이언트로 전송
        objectMapper.writeValue(response.getWriter(), responseBody);
    }
}