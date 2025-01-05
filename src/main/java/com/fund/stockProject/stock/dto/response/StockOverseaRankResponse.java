package com.fund.stockProject.stock.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class StockOverseaRankResponse {
    // 종목 코드
    private String symb;

    // 종목명
    private String name;

    // 현재가
    private String last;

    // 등락 기호
    private String sign;

    // 등락 폭
    private String diff;

    // 등락율
    private String rate;

    // 거래량 (단위: 주)
    private String tvol;

    // 시가총액 (단위: 천)
    private String valx;

    // 순위
    private String rank;
}