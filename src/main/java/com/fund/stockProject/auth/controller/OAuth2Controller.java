package com.fund.stockProject.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/auth/oauth2")
public class OAuth2Controller {

    @GetMapping("/naver")
    @Operation(summary = "네이버 로그인 리다이렉트", description = "네이버 로그인 페이지로 리다이렉트")
    public void redirectToNaver(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/naver");
    }

    @GetMapping("/kakao")
    @Operation(summary = "카카오 로그인 리다이렉트", description = "카카오 로그인 페이지로 리다이렉트")
    public void redirectToKakao(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/kakao");
    }

    @GetMapping("/google")
    @Operation(summary = "구글 로그인 리다이렉트", description = "구글 로그인 페이지로 리다이렉트")
    public void redirectToGoogle(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }
}