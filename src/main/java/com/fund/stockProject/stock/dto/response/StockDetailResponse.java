package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import lombok.*;

import java.util.List;

@Getter
public class StockDetailResponse {

    private Integer stockId;

    private String symbolName;

    private String securityName;

    private String symbol;

    private EXCHANGENUM exchangeNum;

    private COUNTRY country;

    private Double price;

    private Double priceDiff;

    private Double priceDiffPerCent;

    private Integer score;

    private Integer scoreDiff;

    private List<String> keywords;

    @Builder
    public StockDetailResponse(Integer stockId, String symbolName, String securityName, String symbol,
                               EXCHANGENUM exchangeNum, COUNTRY country, Double price, Double priceDiff,
                               Double priceDiffPerCent, Integer score, Integer scoreDiff, List<String> keywords) {
        this.stockId = stockId;
        this.symbolName = symbolName;
        this.securityName = securityName;
        this.symbol = symbol;
        this.exchangeNum = exchangeNum;
        this.country = country;
        this.price = price;
        this.priceDiff = priceDiff;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.scoreDiff = scoreDiff;
        this.keywords = keywords;
    }
}
