package com.fund.stockProject.experiment.dto;

import com.fund.stockProject.stock.domain.COUNTRY;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExperimentInfoResponse {

    private Integer experimentId;

    private String symbolName;

    private LocalDateTime buyAt;

    private Integer buyPrice;

    private Double roi;

    private String status;

    private COUNTRY country;
}
