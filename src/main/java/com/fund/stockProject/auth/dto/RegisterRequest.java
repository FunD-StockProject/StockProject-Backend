package com.fund.stockProject.auth.dto;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class RegisterRequest {
    private String email;
    private String password;
    private String nickname;
    private LocalDate birthDate;
}
