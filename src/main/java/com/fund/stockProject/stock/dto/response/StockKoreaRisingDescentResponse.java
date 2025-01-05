package com.fund.stockProject.stock.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class StockKoreaRisingDescentResponse {
    // 단축 종목코드
    private String stckShrnIscd;

    // 데이터 순위
    private String dataRank;

    // HTS 한글 종목명
    private String htsKorIsnm;

    // 주식 현재가
    private String stckPrpr;

    // 전일 대비
    private String prdyVrss;

    // 전일 대비 부호
    private String prdyVrssSign;

    // 전일 대비율
    private String prdyCtrt;
}