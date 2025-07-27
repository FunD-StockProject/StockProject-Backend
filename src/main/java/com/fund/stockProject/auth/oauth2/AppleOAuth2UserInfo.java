package com.fund.stockProject.auth.oauth2;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class AppleOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getNickname() {
        // Apple은 닉네임(이름)이 대부분 없음. 최초 최초 가입시 name 넘겼다면 attributes에 내려올 수도 있음
        return attributes.containsKey("name") ? (String) attributes.get("name") : null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}

