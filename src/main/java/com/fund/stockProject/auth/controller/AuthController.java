package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.auth.service.TokenService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.sun.security.auth.UserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    @PostMapping("/register")
    @Operation(summary = "회원가입 API", description = "회원가입 API")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody OAuth2RegisterRequest oAuth2RegisterRequest) {
        try {
            authService.register(oAuth2RegisterRequest);
            return ResponseEntity.ok(Map.of("message", "User registered successfully via social login")); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete social registration: " + e.getMessage()));
        }
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴 API", description = "회원 탈퇴 API")
    public ResponseEntity<Map<String, String>> withdrawUser(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        if (customUserDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // HTTP 401 Unauthorized
                    .body(Map.of("message", "Authentication required"));
        }

        try {
            authService.withdrawUser(customUserDetails.getEmail());
            return ResponseEntity.ok(Map.of("message", "User withdrawn successfully")); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to withdraw user: " + e.getMessage()));
        }
    }

    @PostMapping("/reissue")
    @Operation(summary = "ACCESS 토큰 재발급 API", description = "유효한 리프레시 토큰을 사용하여 액세스 토큰을 재발급")
    public ResponseEntity<Map<String, String>> reissue(@RequestBody RefreshTokenRequest request) {
        try {
            tokenService.reissueTokens(request);
            return ResponseEntity.ok(Map.of("message", "Token reissued successfully"));

        } catch (Exception e) {
            if (e instanceof ExpiredJwtException || e.getMessage().contains("expired")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Refresh token expired"));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST) //
                    .body(Map.of("message", "Failed to reissue token: " + e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest requestDto) {
        try {
            tokenService.logout(requestDto.getRefreshToken());
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
        } catch (Exception e) {
            // 예를 들어 유효하지 않은 토큰 포맷 등의 이유로 실패했을 때
            return ResponseEntity.badRequest().body(Map.of("message", "Logout failed: " + e.getMessage()));
        }
    }
}
