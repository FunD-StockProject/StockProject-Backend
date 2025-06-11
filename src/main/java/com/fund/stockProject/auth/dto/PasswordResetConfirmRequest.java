package com.fund.stockProject.auth.dto;

import lombok.Getter;

@Getter
public class PasswordResetConfirmRequest {
    private String email;
    private String token;
    private String newPassword;
}
