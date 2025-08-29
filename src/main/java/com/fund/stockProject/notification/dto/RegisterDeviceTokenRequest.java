package com.fund.stockProject.notification.dto;

import com.fund.stockProject.notification.domain.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "디바이스 토큰 등록 요청 DTO")
public class RegisterDeviceTokenRequest {
    @NotBlank
    @Schema(description = "FCM 디바이스 토큰", example = "fcm_token_abcdef123456")
    private String token;

    @NotNull
    @Schema(description = "디바이스 플랫폼", requiredMode = Schema.RequiredMode.REQUIRED, example = "ANDROID", allowableValues = {"ANDROID","IOS","WEB"})
    private DevicePlatform platform; // enum 으로 제한

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }
}
