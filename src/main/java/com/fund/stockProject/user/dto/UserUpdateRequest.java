package com.fund.stockProject.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Schema(description = "사용자 프로필 수정 요청 DTO. 제공된 필드만 부분 업데이트 됩니다.")
public class UserUpdateRequest {
    @Schema(description = "새 닉네임", example = "human1234")
    private String nickname;
    @JsonProperty("birth_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(description = "생년월일 (yyyy-MM-dd)", example = "1995-04-21")
    private LocalDate birthDate;
    @JsonProperty("marketing_agreement")
    @Schema(description = "마케팅 정보 수신 동의 여부 (null 이면 변경 안 함)", example = "true")
    private Boolean marketingAgreement;
}
