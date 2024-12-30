package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.keyword.entity.Keyword;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class StockDiffResponse {
    private final Integer stockId;

    private final String symbolName;

    private final Integer score;

    private final Integer diff;

    private final List<Keyword> keywords;

    @Builder
    public StockDiffResponse(Integer stockId, String symbolName, Integer score, Integer diff,
        List<Keyword> keywords) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.score = score;
        this.diff = diff;
        this.keywords = keywords;
    }
}
