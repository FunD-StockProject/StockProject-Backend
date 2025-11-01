package com.fund.stockProject.experiment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "실험 매수 응답")
public class ExperimentSimpleResponse {
    @Schema(description = "매수 성공 여부", example = "true")
    private boolean success;
    
    @Schema(description = "응답 메시지", example = "모의 매수 성공")
    private String message;
    
    @Schema(description = "매수가격 (원)", example = "250000")
    private Double price;
}
