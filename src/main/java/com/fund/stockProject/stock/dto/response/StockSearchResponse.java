package com.fund.stockProject.stock.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StockSearchResponse {
    private Integer stockId;
    private String symbol;
    private String symbolName;
    private String securityName;
    private String exchangeNum;

    private Integer scoreId;
    private Integer scoreKorea;
    private Integer scoreOversea;
}
