package com.fund.stockProject.auth.domain;

public enum PROVIDER {
    LOCAL("local"), // 필요하다면 "local" 문자열도 정의할 수 있습니다.
    KAKAO("kakao"),
    NAVER("naver"),
    GOOGLE("google"),
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
