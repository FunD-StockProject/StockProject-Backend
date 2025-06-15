package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.service.TokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class TokenController {
    private final TokenService tokenService;

    @PostMapping("/reissue")
    @Operation(summary = "ACCESS 토큰 재발급 API", description = "유효한 리프레시 토큰을 사용하여 액세스 토큰을 재발급")
    public ResponseEntity<Map<String, String>> reissue(HttpServletRequest request, HttpServletResponse response) {
        try {
            tokenService.reissueTokens(request, response);
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

}