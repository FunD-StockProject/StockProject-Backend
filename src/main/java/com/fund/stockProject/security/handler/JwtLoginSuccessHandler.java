package com.fund.stockProject.security.handler;

import com.fund.stockProject.auth.service.TokenService;
import com.fund.stockProject.security.principle.CustomPrincipal;
import com.fund.stockProject.security.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final TokenService tokenService;
    private final ResponseUtil responseUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        CustomPrincipal customPrincipal = (CustomPrincipal) authentication.getPrincipal();
        String email = customPrincipal.getUsername();

        Collection<? extends GrantedAuthority> authorities = customPrincipal.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority authority = iterator.next();
        String role = authority.getAuthority();

        tokenService.issueTokensOnLogin(response, null, email, role); // 기존 리프레시 토큰은 로그인 시 null로 전달

        responseUtil.sendSuccessResponse(response, "Login successful");
    }
}