package com.fund.stockProject.notification.dto;

import jakarta.validation.constraints.NotBlank;

public class UnregisterDeviceTokenRequest {
    @NotBlank
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

