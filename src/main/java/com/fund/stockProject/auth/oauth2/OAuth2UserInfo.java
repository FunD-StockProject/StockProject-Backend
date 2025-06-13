package com.fund.stockProject.auth.oauth2;

import java.util.Map;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getNickname();
    Map<String, Object> getAttributes();
}
