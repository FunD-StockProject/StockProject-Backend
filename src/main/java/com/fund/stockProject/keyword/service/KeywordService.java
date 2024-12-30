package com.fund.stockProject.keyword.service;

import com.fund.stockProject.keyword.dto.PopularKeywordResponse;
import com.fund.stockProject.keyword.entity.Keyword;
import java.util.List;

import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.stock.entity.Stock;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository stockKeywordRepository;

    public List<Stock> findStocksByKeyword(String keywordName) {
        return stockKeywordRepository.findStocksByKeywordName(keywordName);
    }


    public List<PopularKeywordResponse> findPopularKeyword() {
        final List<Keyword> popularKeyword = stockKeywordRepository.findPopularKeyword();

        return popularKeyword.stream()
            .map(
                keyword -> PopularKeywordResponse.builder()
                    .name(keyword.getName())
                    .freq(keyword.getFrequency())
                    .build()
            ).collect(Collectors.toList());
    }
}
