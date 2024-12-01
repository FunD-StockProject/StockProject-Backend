package com.fund.stockProject.global.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("access_token_token_expired")
    private String accessTokenExpired;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private int expiresIn;

    // Getters
    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessTokenExpired() {
        return accessTokenExpired;
    }

    public String getTokenType() {
        return tokenType;
    }

    public int getExpiresIn() {
        return expiresIn;
    }
}
