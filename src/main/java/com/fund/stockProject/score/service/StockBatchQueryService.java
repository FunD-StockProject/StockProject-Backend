package com.fund.stockProject.score.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockBatchQueryService {

    private static final List<EXCHANGENUM> KOREA_EXCHANGES = List.of(
        EXCHANGENUM.KOSPI,
        EXCHANGENUM.KOSDAQ,
        EXCHANGENUM.KOREAN_ETF
    );

    private static final List<EXCHANGENUM> OVERSEA_EXCHANGES = List.of(
        EXCHANGENUM.NAS,
        EXCHANGENUM.NYS,
        EXCHANGENUM.AMS
    );

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<Integer> getStockIdsByCountry(COUNTRY country) {
        List<EXCHANGENUM> exchanges = country == COUNTRY.KOREA ? KOREA_EXCHANGES : OVERSEA_EXCHANGES;
        return new ArrayList<>(stockRepository.findIdsByExchangeNumIn(exchanges));
    }
}
