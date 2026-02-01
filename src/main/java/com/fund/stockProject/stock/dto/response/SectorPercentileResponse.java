package com.fund.stockProject.stock.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SectorPercentileResponse {

    private final Integer stockId;
    private final String sector;
    private final String sectorName;
    private final Integer score;
    private final Integer rank;
    private final Integer total;
    private final Integer topPercent;

    @Builder
    public SectorPercentileResponse(Integer stockId, String sector, String sectorName, Integer score,
                                    Integer rank, Integer total, Integer topPercent) {
        this.stockId = stockId;
        this.sector = sector;
        this.sectorName = sectorName;
        this.score = score;
        this.rank = rank;
        this.total = total;
        this.topPercent = topPercent;
    }
}
