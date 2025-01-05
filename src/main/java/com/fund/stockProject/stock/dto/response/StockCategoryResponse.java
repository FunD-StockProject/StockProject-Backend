package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.Builder;
import lombok.Getter;

@Getter
public class StockCategoryResponse {

    private final Integer stockId;

    private final String symbolName;

    private final COUNTRY country;

    private final Double price;

    private final Double priceDiff;

    private final Double priceDiffPerCent;

    private final Integer score;

    private final Integer scoreDiff;

    @Builder
    public StockCategoryResponse(Integer stockId, String symbolName, COUNTRY country, Double price, Double priceDiff, Double priceDiffPerCent, Integer score, Integer scoreDiff) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.country = country;
        this.price = price;
        this.priceDiff = priceDiff;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.scoreDiff = scoreDiff;
    }

}
