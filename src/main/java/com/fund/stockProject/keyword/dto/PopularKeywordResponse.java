package com.fund.stockProject.keyword.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class PopularKeywordResponse {
    private final String name;
    private final int freq;

    @Builder
    public PopularKeywordResponse(String name, int freq) {
        this.name = name;
        this.freq = freq;
    }
}
