package com.fund.stockProject.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailFindResponse {
    private String email;
    private String nickname;
}
