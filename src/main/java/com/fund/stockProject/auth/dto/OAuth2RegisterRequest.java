package com.fund.stockProject.auth.dto;

import com.fund.stockProject.auth.domain.PROVIDER;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Getter
@Setter
public class OAuth2RegisterRequest {
    @Schema(description = "사용자 닉네임", example = "human123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
    @Schema(
            description = "사용자 이메일 (Apple 최초 로그인 등 제공되지 않는 경우 생략 가능)",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String email;
    @Schema(description = "OAuth2 제공자", example = "KAKAO", allowableValues = {"KAKAO","NAVER","GOOGLE","APPLE"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private PROVIDER provider;
    @Schema(description = "OAuth2 제공자의 고유 사용자 ID(예: Apple sub)", example = "001234.abcd5678efgh", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String providerId;
    @Schema(description = "생년월일 (yyyy-MM-dd)", example = "1993-08-15")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    @Schema(description = "마케팅 정보 수신 동의 여부", example = "true")
    private Boolean marketingAgreement;
    @Schema(description = "프로필 이미지 파일 (multipart/form-data)", type = "string", format = "binary")
    private MultipartFile image;
}
