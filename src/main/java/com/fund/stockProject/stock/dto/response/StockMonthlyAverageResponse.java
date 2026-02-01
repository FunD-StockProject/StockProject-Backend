package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;
import lombok.Builder;
import lombok.Getter;

@Getter
public class StockMonthlyAverageResponse {

    private final Integer stockId;
    private final String symbolName;
    private final COUNTRY country;
    private final String yearMonth; // yyyy-MM
    private final Integer dataCount;
    private final Double averageScore;

    @Builder
    public StockMonthlyAverageResponse(Integer stockId, String symbolName, COUNTRY country,
                                       String yearMonth, Integer dataCount, Double averageScore) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.country = country;
        this.yearMonth = yearMonth;
        this.dataCount = dataCount;
        this.averageScore = averageScore;
    }
}
