package com.fund.stockProject.experiment.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentStatusResponse {

    private List<ExperimentInfoResponse> progressExperiments; // 진행중인 실험 데이터

    private List<ExperimentInfoResponse> completeExperiments; // 완료된 실험 데이터

    private double avgRoi; // 평균수익률

    private int totalTradeCount; // 총 실험 수

    private int progressTradeCount; // 진행중인 실험 수

    private double successRate; // 성공률
}
