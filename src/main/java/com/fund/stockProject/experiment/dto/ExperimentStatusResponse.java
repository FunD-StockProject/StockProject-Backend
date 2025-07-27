package com.fund.stockProject.experiment.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentStatusResponse {

    private List<ProgressExperimentItemResponse> progressExperimentItems;

    private double avgRoi; // 평균수익률

    private int totalPaperTradeCount; // 총 실험 수

    private int progressPaperTradeCount; // 진행중인 실험 수

    private double successRate; // 성공률
}
