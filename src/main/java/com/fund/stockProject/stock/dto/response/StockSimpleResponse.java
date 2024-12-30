package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.keyword.entity.Keyword;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class StockSimpleResponse {
    private final Integer stockId;

    private final String symbolName;

    private final Integer score;

    private final List<Keyword> keywords;

    @Builder
    public StockSimpleResponse(Integer stockId, String symbolName, Integer score,
        List<Keyword> keywords) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.score = score;
        this.keywords = keywords;
    }
}
