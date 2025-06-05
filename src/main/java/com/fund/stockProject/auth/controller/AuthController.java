package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.OAuth2RegisterRequest;
import com.fund.stockProject.auth.dto.RegisterRequest;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.security.principle.CustomPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> registerProcess(@RequestBody RegisterRequest registerRequest) {
        try {
            authService.registerProcess(registerRequest);
            return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다."); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("회원가입에 실패했습니다: " + e.getMessage()); // HTTP 400 Bad Request
        }
    }

    @PostMapping("/oauth2/register")
    public ResponseEntity<String> completeSocialRegistration(
            @RequestBody OAuth2RegisterRequest oAuth2RegisterRequest,
            @AuthenticationPrincipal CustomPrincipal customPrincipal) {

        if (customPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증되지 않은 사용자입니다."); // HTTP 401 Unauthorized
        }

        try {
            authService.socialJoinProcess(oAuth2RegisterRequest, customPrincipal);
            return ResponseEntity.ok("소셜 회원가입이 성공적으로 완료되었습니다."); // HTTP 200 OK
        } catch (Exception e) {
            System.err.println("Social registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("소셜 회원가입에 실패했습니다: " + e.getMessage()); // HTTP 500 Internal Server Error
        }
    }


}
