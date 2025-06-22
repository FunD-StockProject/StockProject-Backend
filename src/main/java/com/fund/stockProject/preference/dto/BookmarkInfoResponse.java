package com.fund.stockProject.preference.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class BookmarkInfoResponse {
    private final String symbolName;

    private final Double price;

    private final Double priceDiffPerCent;

    private final Integer score;

    private final Integer diff;

    @Builder
    public BookmarkInfoResponse(String symbolName, Double price, Double priceDiffPerCent, Integer score, Integer diff) {
        this.symbolName = symbolName;
        this.price = price;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.diff = diff;
    }
}
