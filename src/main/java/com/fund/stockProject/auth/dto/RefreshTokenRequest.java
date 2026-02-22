package com.fund.stockProject.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Access 토큰 재발급 및 로그아웃에 사용되는 Refresh 토큰 요청 DTO")
public class RefreshTokenRequest {
    @Schema(description = "Refresh 토큰 문자열", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    @Schema(description = "현재 디바이스의 FCM 토큰(로그아웃 시 해당 토큰 비활성화)", example = "fcm_token_abcdef123456", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String deviceToken;
}
