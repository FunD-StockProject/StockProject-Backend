package com.fund.stockProject.stock.dto.response;


import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;

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

    private EXCHANGENUM exchangeNum;

    private COUNTRY country;

    private Integer score; // 점수

    private Integer diff; // 점수 등락폭
}
