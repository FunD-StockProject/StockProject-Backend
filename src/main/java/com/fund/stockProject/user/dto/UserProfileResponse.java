package com.fund.stockProject.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UserProfileResponse {
    private String email;
    private String nickname;
    @JsonProperty("birth_date")
    private LocalDate birthDate;
    @JsonProperty("marketing_agreement")
    private Boolean marketingAgreement;
    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}

