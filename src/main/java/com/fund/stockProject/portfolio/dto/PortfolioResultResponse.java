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

    private Recommend recommend;
    private HumanIndicator humanIndicator;
    private Pattern pattern;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommend {
        private int weeklyExperimentCount;  // 이번 주 실험 진행 횟수
        private Integer bestYieldScore;    // 가장 높은 수익률 실험의 점수
        private Integer worstYieldScore;   // 가장 낮은 수익률 실험의 점수
        private List<ScoreTableItem> scoreTable;  // 점수대별 통계
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreTableItem {
        private int min;              // 구간 최소 점수
        private int max;              // 구간 최대 점수
        private Double avgYieldTotal; // 전체 유저 평균
        private Double avgYieldUser;  // 내 평균
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HumanIndicator {
        private String type;          // 'worst' | 'bad' | 'normal' | 'good' | 'best'
        private int percentile;       // 상위 %
        private double successRate;   // 성공률
        private int totalBuyCount;    // 종목 구매 횟수
        private int successCount;     // 오르는 종목 개수
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pattern {
        private String type;          // 패턴 이름 키 (value-preemptive 등)
        private int percentile;       // 해당 타입 유저 비율
        private List<HistoryPoint> history;  // 차트에 찍을 점들 (최대 10개)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryPoint {
        private String date;          // MM.DD
        private int score;            // X축 (0~100)
        private double yield;         // Y축
        private Integer stockId;      // 종목 ID
        private String stockName;     // 종목명
        private boolean isDuplicateName;  // 리스트 내 이름 중복 여부

        public static String toDateLabel(LocalDateTime dateTime) {
            return dateTime.format(DateTimeFormatter.ofPattern("MM.dd"));
        }
    }
}


