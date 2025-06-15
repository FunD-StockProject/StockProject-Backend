package com.fund.stockProject.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class RegisterRequest {
    private String email;
    private String password;
    private String nickname;
    @JsonProperty("birth_date")
    private LocalDate birthDate;
    @JsonProperty("marketing_agreement")
    private Boolean marketingAgreement;
}
