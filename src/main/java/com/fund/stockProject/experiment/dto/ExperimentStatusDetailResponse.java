package com.fund.stockProject.experiment.dto;

import com.fund.stockProject.stock.domain.COUNTRY;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentStatusDetailResponse {
    private String symbolName; // 종목명
    private Integer stockId; // 종목 ID
    private double roi; // 최종 수익률
    private String status; // 거래 상태
    private List<TradeInfo> tradeInfos; // 거래 내역
    private Integer buyScore; // 매수 시점 점수
    private Integer currentScore; // 현재 시점 점수
    private Integer buyPrice; // 매수 가격
    private Integer currentPrice; // 현재 가격
    private LocalDateTime buyAt; // 매수일
    private COUNTRY country; // 국가

    @Getter
    public static class TradeInfo {
        private double price; // 가격
        private int score; // 점수
        private LocalDateTime tradeAt; // 거래일
        private double roi; // 수익률

        @Builder
        public TradeInfo(Double price, Integer score, LocalDateTime tradeAt, double roi) {
            this.price = price;
            this.score = score;
            this.tradeAt = tradeAt;
            this.roi = roi;
        }
    }
}
