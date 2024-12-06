package com.fund.stockProject.stock.dto.response;

import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class StockChartResponse {

    private final String symbol;
    private final String symbolName;
    private final String securityName;
    private final EXCHANGENUM exchangenum;
    private final COUNTRY country;
    private final List<PriceInfo> priceInfos;

    @Builder
    public StockChartResponse(String symbol, String symbolName, String securityName,
        EXCHANGENUM exchangenum, COUNTRY country, List<PriceInfo> priceInfos) {
        this.symbol = symbol;
        this.symbolName = symbolName;
        this.securityName = securityName;
        this.exchangenum = exchangenum;
        this.country = country;
        this.priceInfos = priceInfos;
    }

    @Getter
    public static class PriceInfo {
        private final String localDate;
        private final String closePrice;
        private final String openPrice;
        private final String highPrice;
        private final String lowPrice;
        private final String accumulatedTradingVolume;
        private final String accumulatedTradingValue;

        private final Integer score;
        private final Integer diff;

        @Builder
        public PriceInfo(String localDate, String closePrice, String openPrice, String highPrice,
            String lowPrice, String accumulatedTradingVolume, String accumulatedTradingValue,
            Integer score, Integer diff) {
            this.localDate = localDate;
            this.closePrice = closePrice;
            this.openPrice = openPrice;
            this.highPrice = highPrice;
            this.lowPrice = lowPrice;
            this.accumulatedTradingVolume = accumulatedTradingVolume;
            this.accumulatedTradingValue = accumulatedTradingValue;
            this.score = score;
            this.diff = diff;
        }
    }

}
