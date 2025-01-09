package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.Builder;
import lombok.Getter;

@Getter
public class StockHotSearchResponse {

    private final Integer stockId;

    private final String symbol;

    private final String symbolName;

    private final COUNTRY country;

    @Builder
    public StockHotSearchResponse(Integer stockId, String symbol, String symbolName, COUNTRY country) {
        this.stockId = stockId;
        this.symbol = symbol;
        this.symbolName = symbolName;
        this.country = country;
    }
}
