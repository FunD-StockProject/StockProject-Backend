package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.security.principle.CustomPrincipal;
import io.swagger.v3.oas.annotations.Hidden;
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

    @Hidden // Swagger 문서에서 숨김
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerProcess(@RequestBody RegisterRequest registerRequest) {
        try {
            authService.registerProcess(registerRequest);
            return ResponseEntity.status(HttpStatus.CREATED) // HTTP 201 Created
                    .body(Map.of("message", "User registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // HTTP 400 Bad Request
                    .body(Map.of("message", "Failed to register user: " + e.getMessage()));
        }

    }

    @PostMapping("/oauth2/register")
    public ResponseEntity<Map<String, String>> completeSocialRegistration(
            @RequestBody OAuth2RegisterRequest oAuth2RegisterRequest,
            @AuthenticationPrincipal CustomPrincipal customPrincipal) {

        if (customPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // HTTP 401 Unauthorized
                    .body(Map.of("message", "Authentication required"));
        }

        try {
            authService.socialJoinProcess(oAuth2RegisterRequest, customPrincipal);
            return ResponseEntity.ok(Map.of("message", "User registered successfully via social login")); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete social registration: " + e.getMessage()));
        }
    }

    @PostMapping("/find-email")
    public ResponseEntity<EmailFindResponse> findEmail(@RequestBody EmailFindRequest emailFindRequest) {
        try {
            EmailFindResponse emailFindResponse = authService.findEmail(emailFindRequest);
            return ResponseEntity.ok(emailFindResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetRequest(@RequestBody PasswordResetEmailRequest passwordResetEmailRequest) {
        try {
            authService.sendResetLink(passwordResetEmailRequest);
            return ResponseEntity.ok(Map.of(
                    "message", "PASSWORD_RESET_EMAIL_SENT"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "PASSWORD_RESET_EMAIL_FAILED"));
        }
    }

    @PostMapping("/reset-password/confirm")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody PasswordResetConfirmRequest passwordResetConfirmRequest) {
        try {
            authService.resetPassword(passwordResetConfirmRequest);
            return ResponseEntity.ok(Map.of("message", "PASSWORD_RESET_SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "PASSWORD_RESET_FAILED: " + e.getMessage()));
        }
    }

    @DeleteMapping("/withdraw")
    public ResponseEntity<Map<String, String>> withdrawUser(@AuthenticationPrincipal CustomPrincipal customPrincipal) {
        if (customPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // HTTP 401 Unauthorized
                    .body(Map.of("message", "Authentication required"));
        }

        try {
            authService.withdrawUser(customPrincipal.getUserEmail());
            return ResponseEntity.ok(Map.of("message", "User withdrawn successfully")); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to withdraw user: " + e.getMessage()));
        }
    }
}
