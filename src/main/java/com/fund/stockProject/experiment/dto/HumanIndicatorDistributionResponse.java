package com.fund.stockProject.experiment.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HumanIndicatorDistributionResponse {
    private long totalUsers; // 완료된 실험 1건 이상 유저 수
    private Map<String, Integer> distribution; // 등급별 유저 비율
}
