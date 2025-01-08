package com.fund.stockProject.keyword.dto;

import com.fund.stockProject.stock.domain.COUNTRY;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class KeywordStockResponse {
    private final String keyword;
    private final Integer stockId;
    private final String symbolName;
    private final Integer score;
    private final Integer diff;
    private final COUNTRY country;
    private final List<String> keywordNames;

    @Builder
    public KeywordStockResponse(String keyword, Integer stockId, String symbolName, Integer score, Integer diff, COUNTRY country,
        List<String> keywordNames) {
        this.keyword = keyword;
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.score = score;
        this.diff = diff;
        this.country = country;
        this.keywordNames = keywordNames;
    }
}
