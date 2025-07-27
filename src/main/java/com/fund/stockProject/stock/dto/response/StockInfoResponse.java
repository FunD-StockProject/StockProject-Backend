package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class StockInfoResponse {

    private Integer stockId;

    private String symbolName;

    private String securityName;

    private String symbol;

    private EXCHANGENUM exchangeNum;

    private COUNTRY country;

    private Double price;

    private Double priceDiff;

    private Double priceDiffPerCent;

    private Double yesterdayPrice;

    private Double todayPrice;
}
