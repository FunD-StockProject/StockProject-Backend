package com.fund.stockProject.stock.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class StockOverseaVolumeRankResponse {
    // 종목코드
    private String symb;

    // 한글명
    private String name;

    // 거래량
    private String tvol;


}