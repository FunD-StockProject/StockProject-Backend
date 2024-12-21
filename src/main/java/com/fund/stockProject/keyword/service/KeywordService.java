package com.fund.stockProject.keyword.service;

import java.util.List;

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
}