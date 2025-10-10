package com.fund.stockProject.experiment.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentReportResponse {
    // 1번 결과 데이터
    private long weeklyExperimentCount; // 이번주 진행한 실험 횟수
    private List<ReportStatisticDto> reportStatisticDtos; // 점수 구간별 평균 수익률

    // 2번 결과 데이터
    private long totalUserExperiments; // 유저가 진행한 전체 실험 횟수
    private long successUserExperiments; // 유저 실험 중 수익에 성공한 실험 횟수
    private long sameGradeUserRate; // 동일 등급의 전체 유저 비율

    private List<ReportPatternDto> reportPatternDtos; // 인간지표 점수 별 투자 유형 패턴 데이터
}
