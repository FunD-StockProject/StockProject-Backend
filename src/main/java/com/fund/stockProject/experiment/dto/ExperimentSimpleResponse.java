package com.fund.stockProject.experiment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentSimpleResponse {
    private boolean success;
    private String message;
    private Double price;
}
