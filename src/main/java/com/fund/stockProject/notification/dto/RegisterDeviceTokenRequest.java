package com.fund.stockProject.notification.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterDeviceTokenRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String platform; // consider restricting to enum values (e.g., ANDROID, IOS, WEB)

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}

