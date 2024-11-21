package com.fund.stockProject.score.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ScoreResponse {
    private final Integer score;

    @Builder
    public ScoreResponse(Integer score) {this.score = score;}
}
