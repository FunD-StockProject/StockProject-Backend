package com.fund.stockProject.score.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ScoreResponse {
    private final Integer scoreKorea;

    private final Integer scoreOversea;

    @Builder
    public ScoreResponse(Integer scoreKorea, Integer scoreOversea) {
        this.scoreKorea = scoreKorea;
        this.scoreOversea = scoreOversea;
    }

}
