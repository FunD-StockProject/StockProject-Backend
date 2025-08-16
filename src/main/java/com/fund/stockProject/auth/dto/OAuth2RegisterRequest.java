package com.fund.stockProject.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fund.stockProject.auth.domain.PROVIDER;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Getter
@Setter
public class OAuth2RegisterRequest {
    private String nickname;
    private String email;
    private PROVIDER provider;
    @JsonProperty("birth_date") // @ModelAttribute에서는 'birthDate' 키로 전송 필요
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    @JsonProperty("marketing_agreement") // @ModelAttribute에서는 'marketingAgreement' 키로 전송 필요
    private Boolean marketingAgreement;

    // 멀티파트 폼에서 바인딩될 이미지 파일
    private MultipartFile image;
}
