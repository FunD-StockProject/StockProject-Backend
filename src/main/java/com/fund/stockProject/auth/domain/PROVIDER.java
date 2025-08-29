package com.fund.stockProject.auth.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth2 인증 제공자 구분 값.\nKAKAO: 카카오 OAuth2\nNAVER: 네이버 OAuth2\nGOOGLE: 구글 OAuth2\nAPPLE: 애플 OAuth2")
public enum PROVIDER {
    /** 카카오 OAuth2 */
    KAKAO("kakao"),
    /** 네이버 OAuth2 */
    NAVER("naver"),
    /** 구글 OAuth2 */
    GOOGLE("google"),
    /** 애플 OAuth2 */
    APPLE("apple");

    private final String provider;

    PROVIDER(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }

    // String 값을 Provider Enum으로 변환하는 핵심 메소드
    public static PROVIDER fromString(String string) {
        for (PROVIDER provider : PROVIDER.values()) {
            if (provider.provider.equalsIgnoreCase(string)) { // 대소문자 구분 없이 비교
                return provider;
            }
        }
        // 일치하는 값이 없을 경우 예외 발생 또는 다른 기본값 처리
        throw new IllegalArgumentException("Invalid provider value: " + string);
    }
}
