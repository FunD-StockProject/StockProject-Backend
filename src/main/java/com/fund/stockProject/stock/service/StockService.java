package com.fund.stockProject.stock.service;

import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockQueryRepository stockQueryRepository;

    public StockService(StockRepository stockRepository, StockQueryRepository stockQueryRepository) {
        this.stockRepository = stockRepository;
        this.stockQueryRepository = stockQueryRepository;
    }

    public StockSearchResponse searchStockBySymbolName(final String symbolName) {
        final Stock stock = stockRepository.findStockBySymbolName(symbolName)
            .orElseThrow(NoSuchElementException::new);

        return StockSearchResponse.builder()
            .stockId(stock.getId())
            .symbol(stock.getSymbol())
            .symbolName(stock.getSymbolName())
            .securityName(stock.getSecurityName())
            .exchangeNum(stock.getExchangeNum())
            .scoreId(stock.getScore().getId())
            .scoreKorea(stock.getScore().getScoreKorea())
            .scoreOversea(stock.getScore().getScoreOversea())
            .build();
    }


    public List<StockSearchResponse> autoCompleteKeyword(String keyword) {
        final List<Stock> stocks = stockQueryRepository.autocompleteKeyword(keyword);

        if(stocks.isEmpty()){
            throw new NoSuchElementException();
        }

        return stocks.stream()
            .map(stock -> StockSearchResponse.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .symbolName(stock.getSymbolName())
                .securityName(stock.getSecurityName())
                .exchangeNum(stock.getExchangeNum())
                .scoreId(stock.getScore().getId())
                 .scoreKorea(stock.getScore().getScoreKorea())
                 .scoreOversea(stock.getScore().getScoreOversea())
                .build())
            .collect(Collectors.toList());
    }
}
