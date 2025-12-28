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

    private Double buyPrice;

    private Double roi;

    private String status;

    private COUNTRY country;
    
    private Integer buyScore; // 매수 시점 점수
    
    private Integer currentScore; // 현재 시점 점수
    
    private Double currentPrice; // 현재 가격
    
    private Integer stockId; // 종목 ID
}
