package com.fund.stockProject.stock.dto.response;


import com.fund.stockProject.stock.domain.COUNTRY;

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

    private COUNTRY country;
}
