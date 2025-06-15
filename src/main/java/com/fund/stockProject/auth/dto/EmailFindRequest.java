package com.fund.stockProject.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class EmailFindRequest {
    private String nickname;
    @JsonProperty("birth_date")
    private LocalDate birthDate;
}
