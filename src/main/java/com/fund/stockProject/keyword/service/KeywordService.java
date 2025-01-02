package com.fund.stockProject.keyword.service;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.entity.Stock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository stockKeywordRepository;

    public List<Stock> findStocksByKeyword(String keywordName) {
        return stockKeywordRepository.findStocksByKeywordName(keywordName);
    }


    public List<String> findPopularKeyword(final COUNTRY country) {
        List<Keyword> popularKeywords = null;
        Pageable pageable = PageRequest.of(0, 100);

        if (COUNTRY.KOREA.equals(country)) {
            List<EXCHANGENUM> exchanges = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF);
            popularKeywords = stockKeywordRepository.findPopularKeyword(exchanges, pageable);
        } else if (COUNTRY.OVERSEA.equals(country)) {
            List<EXCHANGENUM> exchanges = List.of(EXCHANGENUM.NAS, EXCHANGENUM.NYS, EXCHANGENUM.AMS);
            popularKeywords = stockKeywordRepository.findPopularKeyword(exchanges, pageable);
        }

        final Set<String> keywordNames = new HashSet<>();

        for (Keyword keyword : popularKeywords) {
            if (keywordNames.size() >= 9) {
                break; // 크기 조건을 먼저 확인해 불필요한 작업 방지
            }

            String name = keyword.getName();
            if (!keywordNames.contains(name) && name.matches("^[a-zA-Z0-9가-힣\\s]+$")) {
                keywordNames.add(name);
            }
        }

        return new ArrayList<>(keywordNames);
    }
}
