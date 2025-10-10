package com.fund.stockProject.experiment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportStatisticDto {
    private String scoreRange;
    private double totalAvgRoi;
    private double userAvgRoi;
}
