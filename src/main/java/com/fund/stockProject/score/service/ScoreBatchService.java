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

        int processedCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (Integer stockId : targetStockIds) {
            if (scoreRepository.existsByStockIdAndDate(stockId, today)) {
                skippedCount++;
                continue;
            }

            processedCount++;
            try {
                int yesterdayScore = resolveYesterdayScore(stockId, country, yesterday, today);
                scoreService.updateScoreAndKeyword(stockId, country, yesterdayScore);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Error processing score for stockId: {}", stockId, e);
            }
        }

        log.info("Score batch completed for {}: processed={}, success={}, skipped={}, errors={}", 
            country, processedCount, successCount, skippedCount, errorCount);
    }

    public void runIndexBatch() {
        try {
            scoreService.updateIndexScore();
        } catch (Exception e) {
            log.error("Error processing index scores", e);
        }
    }

    /**
     * 어제 점수를 조회합니다. 어제 데이터가 없으면 최신 데이터를 사용합니다.
     * 
     * @param stockId 종목 ID
     * @param country 국가 (KOREA 또는 OVERSEA)
     * @param yesterday 어제 날짜
     * @param today 오늘 날짜
     * @return 어제 점수 (없으면 최신 점수, 그것도 없으면 0)
     */
    private int resolveYesterdayScore(Integer stockId, COUNTRY country, LocalDate yesterday, LocalDate today) {
        // 1. 어제 날짜의 Score 조회 시도
        return scoreRepository.findByStockIdAndDate(stockId, yesterday)
            .map(score -> {
                int scoreValue = country == COUNTRY.KOREA ? score.getScoreKorea() : score.getScoreOversea();
                // 9999는 유효하지 않은 값이므로 최신 데이터를 찾아야 함
                if (scoreValue == 9999) {
                    return findValidPreviousScore(stockId, country, today);
                }
                return scoreValue;
            })
            // 2. 어제 데이터가 없으면 최신 데이터 조회 (오늘 제외)
            .or(() -> findValidPreviousScoreOptional(stockId, country, today))
            // 3. 모든 데이터가 없으면 0 반환
            .orElse(0);
    }

    /**
     * 유효한 이전 점수를 찾습니다 (9999가 아닌 값).
     * 
     * @param stockId 종목 ID
     * @param country 국가
     * @param excludeDate 제외할 날짜 (오늘)
     * @return 유효한 이전 점수 (Optional), 없으면 빈 Optional
     */
    private java.util.Optional<Integer> findValidPreviousScoreOptional(Integer stockId, COUNTRY country, LocalDate excludeDate) {
        return scoreRepository.findTopByStockIdOrderByDateDesc(stockId)
            .filter(score -> !score.getDate().isEqual(excludeDate))
            .map(score -> country == COUNTRY.KOREA ? score.getScoreKorea() : score.getScoreOversea())
            .filter(scoreValue -> scoreValue != 9999);
    }

    /**
     * 유효한 이전 점수를 찾습니다 (9999가 아닌 값).
     * 
     * @param stockId 종목 ID
     * @param country 국가
     * @param excludeDate 제외할 날짜 (오늘)
     * @return 유효한 이전 점수, 없으면 0
     */
    private int findValidPreviousScore(Integer stockId, COUNTRY country, LocalDate excludeDate) {
        return findValidPreviousScoreOptional(stockId, country, excludeDate).orElse(0);
    }
}
