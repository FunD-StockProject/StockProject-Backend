package com.fund.stockProject.keyword.service;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.keyword.repository.StockKeywordRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.service.SecurityService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final StockKeywordRepository stockKeywordRepository;
    private final SecurityService securityService;

    public List<StockInfoResponse> findStocksByKeyword(String keywordName) {
        final List<StockKeyword> byKeywordName = stockKeywordRepository.findByKeywordName(keywordName);
        final List<StockInfoResponse> stocks = new ArrayList<>();

        for (final StockKeyword stockKeyword : byKeywordName) {
            if(stocks.size() >= 15){
                break;
            }

            final Stock stock = stockKeyword.getStock();
            final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(), stock.getSecurityName(), stock.getSymbol(),
                stock.getExchangeNum(), List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF).contains(stock.getExchangeNum()) ? COUNTRY.KOREA : COUNTRY.OVERSEA);

            stocks.add(securityStockInfoKorea.block());
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
