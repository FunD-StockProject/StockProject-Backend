package com.fund.stockProject.experiment.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ReportPatternDto {
    private double roi; // 수익률
    private int score; // 인간지표 점수
    private LocalDateTime buyAt; // 매수날짜
}
