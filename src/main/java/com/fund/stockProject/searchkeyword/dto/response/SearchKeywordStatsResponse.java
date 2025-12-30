package com.fund.stockProject.searchkeyword.dto.response;

import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchKeywordStatsResponse {
    private String keyword;
    private COUNTRY country;
    private Long searchCount;
}
