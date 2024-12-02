package com.fund.stockProject.stock.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class StockWordResponse {
    private final String word;
    private final Integer freq;

    @Builder
    public StockWordResponse(String word, Integer freq) {
        this.word = word;
        this.freq = freq;
    }
}
