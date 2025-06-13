package com.fund.stockProject.auth.oauth2;

import java.util.Map;

public class NaverOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;
    private final Map<String, Object> response;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.response = (Map<String, Object>) attributes.get("response");
    }
    @Override
    public String getProviderId() {
        if (response == null) {
            return null;
        }
        return (String) response.get("id");
    }

    @Override
    public String getEmail() {
        if (response == null) {
            return null;
        }
        return (String) response.get("email");    }

    @Override
    public String getNickname() {
        if (response == null) {
            return null;
        }
        return (String) response.get("nickname");    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}