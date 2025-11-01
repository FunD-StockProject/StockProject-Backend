package com.fund.stockProject.portfolio.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResultResponse {

    private List<ScoreTableItem> scoreTable;
    private ExperimentSummary experimentSummary;
    private HumanIndex humanIndex;
    private InvestmentPattern investmentPattern;
    private List<HistoryPoint> history;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreTableItem {
        private String range;
        private Double avg;   // 사용자 평균
        private Double median; // 스펙 미정: 일단 null 유지
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentSummary {
        private long totalExperiments;
        private ProfitBound highestProfit;
        private ProfitBound lowestProfit;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfitBound {
        private Integer score;
        private String range;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HumanIndex {
        private Integer userScore; // 미정: null
        private String userType;   // 미정: null
        private String successRate; // 예: "0~20%"
        private String maintainRate; // 미정: null
        private long purchasedCount;
        private long profitCount;
        private Long sameGradeUserRate; // 전체 유저 중 동일 등급 유저 비율 (%)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestmentPattern {
        private String patternType;        // 미정: null
        private String patternDescription; // 미정: null
        private Double avgScore;           // 사용자 평균 점수 (사분면 분류 기준)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryPoint {
        private int x;      // score
        private double y;   // roi
        private String label; // MMdd

        public static String toLabel(LocalDateTime dateTime) {
            return dateTime.format(DateTimeFormatter.ofPattern("MMdd"));
        }
    }
}


