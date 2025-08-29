package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.dto.RegisterDeviceTokenRequest;
import com.fund.stockProject.notification.dto.UnregisterDeviceTokenRequest;
import com.fund.stockProject.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Notification")
@RestController
@RequestMapping("/device-tokens")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * FCM 토큰 등록
     */
    @PostMapping
    @Operation(summary = "FCM 디바이스 토큰 등록", description = "사용자 디바이스의 FCM 토큰을 저장/업데이트합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (누락/중복/형식 오류)", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> registerToken(
            @AuthenticationPrincipal(expression = "id") @Parameter(hidden = true) Integer userId,
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
    @Operation(summary = "FCM 디바이스 토큰 삭제", description = "사용자 계정에서 지정한 FCM 디바이스 토큰을 제거합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (존재하지 않음/형식 오류)", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> unregisterToken(
            @AuthenticationPrincipal(expression = "id") @Parameter(hidden = true) Integer userId,
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
