package com.fund.stockProject.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.dto.LoginRequest;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RequiredArgsConstructor
public class JwtLoginFilter extends UsernamePasswordAuthenticationFilter {
    private final AuthenticationManager authenticationManager;

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        // 이메일을 고유 식별자로 사용하기 때문에 username은 이메일로 설정
        String email = "";
        String password = "";

        // 1) Content-Type 체크
        if (request.getContentType() != null
                && request.getContentType().contains(APPLICATION_JSON_VALUE)) {
            // 2) JSON 파싱
            try (ServletInputStream is = request.getInputStream()) {

                ObjectMapper om = new ObjectMapper();
                LoginRequest loginRequest = om.readValue(is, LoginRequest.class);

                email = loginRequest.getEmail();
                password = loginRequest.getPassword();

            } catch (IOException e) {
                throw new AuthenticationServiceException("Failed to parse login request", e);
            }
        }
        // 3) 토큰 생성 후 인증 매니저에 위임
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(email, password);

        return authenticationManager.authenticate(authRequest);
    }
}
