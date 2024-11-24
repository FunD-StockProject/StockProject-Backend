package com.fund.stockProject.stock.dto.response;


import jakarta.persistence.Column;
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

    private String region; // 주식 지역(국내/해외)

    private Integer scoreKorea;
    private Integer scoreOversea;
    private Integer scoreNaver;
    private Integer scorePax;
}
