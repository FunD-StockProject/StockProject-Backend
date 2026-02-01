package com.fund.stockProject.stock.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SectorAverageResponse {

    private final String sector;
    private final String sectorName;
    private final Integer averageScore;
    private final Integer count;

    @Builder
    public SectorAverageResponse(String sector, String sectorName, Integer averageScore, Integer count) {
        this.sector = sector;
        this.sectorName = sectorName;
        this.averageScore = averageScore;
        this.count = count;
    }
}
