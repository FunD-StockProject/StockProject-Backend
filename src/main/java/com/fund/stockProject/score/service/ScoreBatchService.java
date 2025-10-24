package com.fund.stockProject.score.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreBatchService {

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

    private final ScoreService scoreService;
    private final ScoreRepository scoreRepository;
    private final StockRepository stockRepository;

    public void runCountryBatch(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Stock> targetStocks = getStocksByCountry(country);
        log.info("Starting score batch for {} with {} candidate stocks", country, targetStocks.size());

        for (Stock stock : targetStocks) {
            if (scoreRepository.existsByStockIdAndDate(stock.getId(), today)) {
                continue;
            }

            try {
                int yesterdayScore = resolveYesterdayScore(stock.getId(), country, yesterday, today);
                scoreService.updateScoreAndKeyword(stock.getId(), country, yesterdayScore);
            } catch (Exception e) {
                log.error("Error processing score {}", stock.getId(), e);
            }
        }
    }

    public void runIndexBatch() {
        try {
            scoreService.updateIndexScore();
        } catch (Exception e) {
            log.error("Error processing index scores", e);
        }
    }

    private List<Stock> getStocksByCountry(COUNTRY country) {
        List<EXCHANGENUM> exchanges = country == COUNTRY.KOREA ? KOREA_EXCHANGES : OVERSEA_EXCHANGES;
        return stockRepository.findByExchangeNumIn(exchanges);
    }

    private int resolveYesterdayScore(Integer stockId, COUNTRY country, LocalDate yesterday, LocalDate today) {
        return scoreRepository.findByStockIdAndDate(stockId, yesterday)
            .or(() -> scoreRepository.findTopByStockIdOrderByDateDesc(stockId)
                .filter(score -> !score.getDate().isEqual(today)))
            .map(score -> country == COUNTRY.KOREA ? score.getScoreKorea() : score.getScoreOversea())
            .orElse(0);
    }
}
