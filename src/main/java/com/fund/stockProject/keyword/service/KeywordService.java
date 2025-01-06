package com.fund.stockProject.keyword.service;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.entity.Stock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

        return popularKeywords.stream()
            .map(Keyword::getName)
            .filter(this::isValidKeyword)
            .distinct()
            .limit(9)
            .collect(Collectors.toList());
    }

    private boolean isValidKeyword(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String specialCharsPattern = "^[a-zA-Z0-9가-힣\\s]+$";
        String postfixPattern = "^(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|크|등|또|전).*|.*(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|하|등|또|전)$";

        return name.matches(specialCharsPattern) && !name.matches(postfixPattern);
    }
}
