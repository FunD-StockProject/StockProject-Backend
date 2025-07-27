package com.fund.stockProject.experiment.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProgressExperimentItemResponse {

    private Integer id;

    private String symbolName;

    private LocalDateTime buyAt;

    private Integer buyPrice;

    private Double currentPrice;

    private Integer autoSellIn;

    private Double diffPrice;

    private Double roi;

    private String tradeStatus;
}
