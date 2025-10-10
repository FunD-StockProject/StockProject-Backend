package com.fund.stockProject.experiment.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SCORERANGE {
    RANGE_0_59("60점 미만"),
    RANGE_60_69("60-69점"),
    RANGE_70_79("70-79점"),
    RANGE_80_89("80-89점"),
    RANGE_90_100("90점 이상");

    private final String range;

    SCORERANGE(String range) {
        this.range = range;
    }

    @JsonValue
    public String getRange() {
        return range;
    }

    @JsonCreator
    public static SCORERANGE fromRange(String range) {
        for (SCORERANGE scorerange : SCORERANGE.values()) {
            if (scorerange.range.equals(range)) {
                return scorerange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange range: " + range);
    }
}
