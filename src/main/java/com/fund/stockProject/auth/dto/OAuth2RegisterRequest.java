package com.fund.stockProject.auth.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class OAuth2RegisterRequest {
    private String nickname;
    private String email;
    private LocalDate birthDate;
}
