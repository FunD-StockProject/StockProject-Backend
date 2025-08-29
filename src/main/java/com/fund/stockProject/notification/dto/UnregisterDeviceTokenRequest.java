package com.fund.stockProject.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "디바이스 토큰 해제(비활성화) 요청 DTO")
public class UnregisterDeviceTokenRequest {
    @NotBlank
    @Schema(description = "해제할 FCM 디바이스 토큰", example = "fcm_token_abcdef123456")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
