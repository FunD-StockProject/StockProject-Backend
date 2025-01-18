package com.fund.stockProject.keyword.service;

import com.fund.stockProject.keyword.dto.KeywordStockResponse;
import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.keyword.repository.StockKeywordRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.service.SecurityService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final StockKeywordRepository stockKeywordRepository;
    private final SecurityService securityService;

    public List<KeywordStockResponse> findStocksByKeyword(String keywordName) {
        final List<StockKeyword> byKeywordName = stockKeywordRepository.findByKeywordName(keywordName);
        final List<KeywordStockResponse> stocks = new ArrayList<>();

        for (final StockKeyword stockKeyword : byKeywordName) {
            if(stocks.size() >= 15){
                break;
            }

            final Stock stock = stockKeyword.getStock();
            final StockInfoResponse stockInfoResponse = securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(), stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(),
                List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF).contains(stock.getExchangeNum()) ? COUNTRY.KOREA : COUNTRY.OVERSEA).block();
            final List<String> keywordNames = keywordRepository.findKeywordsByStockId(stock.getId(), PageRequest.of(0, 2))
                .stream().map(Keyword::getName).toList();

            stocks.add(KeywordStockResponse.builder()
                    .stockId(stockInfoResponse.getStockId())
                    .keyword(keywordName)
                    .country(stockInfoResponse.getCountry())
                    .symbolName(stockInfoResponse.getSymbolName())
                    .keywordNames(keywordNames)
                    .score(stock.getExchangeNum()
                        .equals(EXCHANGENUM.KOSPI) || stock.getExchangeNum()
                        .equals(EXCHANGENUM.KOSDAQ) || stock.getExchangeNum()
                        .equals(EXCHANGENUM.KOREAN_ETF) ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea())
                    .diff(stock.getScores().get(0).getDiff())
                .build());
        }

        return stocks;
    }

    public List<String> findPopularKeyword(final COUNTRY country) {
        List<Keyword> popularKeywords = null;
        Pageable pageable = PageRequest.of(0, 100);

        if (COUNTRY.KOREA.equals(country)) {
            List<EXCHANGENUM> exchanges = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ,
                EXCHANGENUM.KOREAN_ETF);
            popularKeywords = keywordRepository.findPopularKeyword(exchanges, pageable);
        } else if (COUNTRY.OVERSEA.equals(country)) {
            List<EXCHANGENUM> exchanges = List.of(EXCHANGENUM.NAS, EXCHANGENUM.NYS,
                EXCHANGENUM.AMS);
            popularKeywords = keywordRepository.findPopularKeyword(exchanges, pageable);
        }

        return popularKeywords.stream()
            .map(Keyword::getName)
            .filter(this::isValidKeyword)
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    private boolean isValidKeyword(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String specialCharsPattern = "^[a-zA-Z0-9가-힣\\s]+$";
        String postfixPattern = "^(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|크|등|또|전|있다|있다.|이다|이다.|있는).*|.*(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|하|등|또|전)$";

        return name.matches(specialCharsPattern) && !name.matches(postfixPattern);
    }

    public List<String> findKeywordRanking() {
        // 현재 날짜와 어제 날짜를 선언
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // top 100 키워드를 조회 (오늘 또는 어제 날짜 조건을 포함)
        final List<Keyword> topKeywords = keywordRepository.findKeywords(today, yesterday, PageRequest.of(0, 100));

        // 유효한 키워드를 필터링하고 상위 10개만 반환
        return topKeywords.stream()
                          .map(Keyword::getName)
                          .filter(this::isValidKeyword)
                          .distinct()
                          .limit(10)
                          .toList();
    }
}
