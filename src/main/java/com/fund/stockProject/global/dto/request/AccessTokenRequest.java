package com.fund.stockProject.global.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessTokenRequest {
    private final String grantType;
    private final String appkey;
    private final String appsecret;

    public AccessTokenRequest(String grantType, String appkey, String appsecret) {
        this.grantType = grantType;
        this.appkey = appkey;
        this.appsecret = appsecret;
    }

    @JsonProperty("grant_type")
    public String getGrantType() {
        return grantType;
    }

    @JsonProperty("appkey")
    public String getAppkey() {
        return appkey;
    }

    @JsonProperty("appsecret")
    public String getAppsecret() {
        return appsecret;
    }
}
