package com.fund.stockProject.stock.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class StockRelevantResponse {
    private final Integer stockId;
    private final String symbolName;
    private final Integer score;
    private final Integer diff;
    private final List<String> keywords;

    @Builder
    public StockRelevantResponse(Integer stockId, String symbolName, Integer score, Integer diff, List<String> keywords) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.score = score;
        this.diff = diff;
        this.keywords = keywords;
    }
}
