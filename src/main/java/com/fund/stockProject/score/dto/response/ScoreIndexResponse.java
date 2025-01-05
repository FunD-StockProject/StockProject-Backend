package com.fund.stockProject.score.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ScoreIndexResponse {
    private final Integer kospiVix;

    private final Integer kospiVixDiff;

    private final Integer kospiIndex;

    private final Integer kospiIndexDiff;

    private final Integer kosdaqIndex;

    private final Integer kosdaqIndexDiff;

    private final Integer snpVix;

    private final Integer snpVixDiff;

    private final Integer snpIndex;

    private final Integer snpIndexDiff;

    private final Integer nasdaqIndex;

    private final Integer nasdaqIndexDiff;

    @Builder
    public ScoreIndexResponse(Integer kospiVix, Integer kospiVixDiff, Integer kospiIndex,
                              Integer kospiIndexDiff,
                              Integer kosdaqIndex, Integer kosdaqIndexDiff, Integer snpVix, Integer snpVixDiff, Integer snpIndex,
                              Integer snpIndexDiff, Integer nasdaqIndex, Integer nasdaqIndexDiff) {
        this.kospiVix = kospiVix;
        this.kospiVixDiff = kospiVixDiff;
        this.kospiIndex = kospiIndex;
        this.kospiIndexDiff = kospiIndexDiff;
        this.kosdaqIndex = kosdaqIndex;
        this.kosdaqIndexDiff = kosdaqIndexDiff;
        this.snpVix = snpVix;
        this.snpVixDiff = snpVixDiff;
        this.snpIndex = snpIndex;
        this.snpIndexDiff = snpIndexDiff;
        this.nasdaqIndex = nasdaqIndex;
        this.nasdaqIndexDiff = nasdaqIndexDiff;
    }
}
