package com.fund.stockProject.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class PasswordResetConfirmRequest {
    private String email;
    private String token;
    @JsonProperty("new_password")
    private String newPassword;
}
