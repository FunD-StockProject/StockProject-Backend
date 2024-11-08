package com.fund.stockProject.stock.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SearchResponse {
    private Integer stockId;
    private String symbol;
    private String symbolName;
    private String securityName;
    private String exchangeNum;

    private Integer scoreId;
    private Integer scoreFinal;
    private Integer scoreNaver;
    private Integer scoreReddit;
}
