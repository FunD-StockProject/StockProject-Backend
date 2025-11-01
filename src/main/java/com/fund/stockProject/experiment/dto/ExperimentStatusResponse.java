package com.fund.stockProject.experiment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "실험 현황 응답")
public class ExperimentStatusResponse {

    @Schema(description = "진행 중인 실험 목록 (5영업일 미만)", example = "[]")
    private List<ExperimentInfoResponse> progressExperiments;

    @Schema(description = "완료된 실험 목록 (5영업일 경과 후 자동 매도)", example = "[]")
    private List<ExperimentInfoResponse> completeExperiments;

    @Schema(description = "전체 실험 평균 수익률 (%)", example = "5.2")
    private double avgRoi;

    @Schema(description = "총 실험 수", example = "10")
    private int totalTradeCount;

    @Schema(description = "진행 중인 실험 수", example = "5")
    private int progressTradeCount;

    @Schema(description = "성공률 (0.0 ~ 1.0, 수익이 난 실험의 비율)", example = "0.6")
    private double successRate;
}
