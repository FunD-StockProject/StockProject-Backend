package com.fund.stockProject.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "로그인 / 토큰 재발급 응답 DTO")
public class LoginResponse {
    @Schema(description = "처리 상태", example = "SUCCESS")
    private String state;

    @Schema(description = "사용자 이메일", example = "user@example.com")
    private String email;

    @Schema(description = "사용자 닉네임", example = "human123")
    private String nickname;

    @Schema(description = "프로필 이미지 URL", example = "https://example-bucket.s3.ap-northeast-2.amazonaws.com/users/profile.jpg")
    private String profileImageUrl;

    @Schema(description = "액세스 토큰(JWT)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "리프레시 토큰(JWT)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("refresh_token")
    private String refreshToken;
}
