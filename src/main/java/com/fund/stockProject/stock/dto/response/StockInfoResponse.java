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

    /**
     * 1 : 상한가
     * 2 : 상승
     * 3 : 보합(변화0)
     * 4 : 하한가
     * 5 : 하락
     */
    private Integer priceSign;


}
