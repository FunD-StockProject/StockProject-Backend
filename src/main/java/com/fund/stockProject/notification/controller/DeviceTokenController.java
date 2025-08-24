package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.dto.RegisterDeviceTokenRequest;
import com.fund.stockProject.notification.dto.UnregisterDeviceTokenRequest;
import com.fund.stockProject.notification.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * FCM 토큰 등록
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> registerToken(
            @AuthenticationPrincipal(expression = "id") Integer userId,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        try {
            deviceTokenService.registerToken(userId, request);
            return ResponseEntity.status(201).body(Map.of("message", "Token registered successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to register token"));
        }
    }

    /**
     * FCM 토큰 삭제
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> unregisterToken(
            @AuthenticationPrincipal(expression = "id") Integer userId,
            @Valid @RequestBody UnregisterDeviceTokenRequest request) {
        try {
            deviceTokenService.unregisterToken(userId, request);
            return ResponseEntity.ok(Map.of("message", "Token unregistered successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to unregister token"));
        }
    }
}
