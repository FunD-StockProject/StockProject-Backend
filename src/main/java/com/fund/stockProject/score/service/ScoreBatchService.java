package com.fund.stockProject.score.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreBatchService {

    private final ScoreService scoreService;
    private final ScoreRepository scoreRepository;
    private final StockBatchQueryService stockBatchQueryService;

    public void runCountryBatch(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Integer> targetStockIds = stockBatchQueryService.getStockIdsByCountry(country);
        log.info("Starting score batch for {} with {} candidate stocks", country, targetStockIds.size());

        for (Integer stockId : targetStockIds) {
            if (scoreRepository.existsByStockIdAndDate(stockId, today)) {
                continue;
            }

            try {
                int yesterdayScore = resolveYesterdayScore(stockId, country, yesterday, today);
                scoreService.updateScoreAndKeyword(stockId, country, yesterdayScore);
            } catch (Exception e) {
                log.error("Error processing score {}", stockId, e);
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

    private int resolveYesterdayScore(Integer stockId, COUNTRY country, LocalDate yesterday, LocalDate today) {
        return scoreRepository.findByStockIdAndDate(stockId, yesterday)
            .or(() -> scoreRepository.findTopByStockIdOrderByDateDesc(stockId)
                .filter(score -> !score.getDate().isEqual(today)))
            .map(score -> country == COUNTRY.KOREA ? score.getScoreKorea() : score.getScoreOversea())
            .orElse(0);
    }
}
