package com.fund.stockProject.stock.service;

import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;

import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public StockSearchResponse searchStockBySymbolName(final String symbolName) {
        final Stock stock = stockRepository.findStockBySymbolName(symbolName).orElseThrow(NoSuchElementException::new);

        return StockSearchResponse.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .symbolName(stock.getSymbolName())
                .securityName(stock.getSecurityName())
                .exchangeNum(stock.getExchangeNum())
                .scoreId(stock.getScore().getId())
                .scoreNaver(stock.getScore().getScoreNaver())
                .scoreReddit(stock.getScore().getScoreReddit())
                .scoreFinal(stock.getScore().getScoreFinal())
                .build();
    }
}
