package com.fund.stockProject.keyword.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KeywordDto {
    private final String word;
    private final int freq;
}