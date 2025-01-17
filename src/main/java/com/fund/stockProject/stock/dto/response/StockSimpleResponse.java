package com.fund.stockProject.stock.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class StockSimpleResponse {

    private final Integer stockId;

    private final String symbolName;

    private final Integer score;

    private final Integer diff;

    @Builder
    public StockSimpleResponse(Integer stockId, String symbolName, Integer score, Integer diff) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.score = score;
        this.diff = diff;
    }
}
