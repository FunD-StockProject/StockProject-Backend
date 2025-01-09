package com.fund.stockProject.stock.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EXCHANGENUM {
    NAS("512"),
    NYS("513"),
    AMS("529"),
    KOSPI("001"),
    KOSDAQ("002"),
    KOREAN_ETF("003");

    private final String code;

    EXCHANGENUM(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static EXCHANGENUM fromCode(String code) {
        for (EXCHANGENUM exchange : EXCHANGENUM.values()) {
            if (exchange.code.equals(code)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange code: " + code);
    }
}
