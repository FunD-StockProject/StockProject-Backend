package com.fund.stockProject.score.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.keyword.repository.StockKeywordRepository;
import com.fund.stockProject.notification.service.StockScoreAlertService;
import com.fund.stockProject.score.dto.response.ScoreKeywordResponse;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScorePersistenceService {

    private final ScoreRepository scoreRepository;
    private final StockRepository stockRepository;
    private final KeywordRepository keywordRepository;
    private final StockKeywordRepository stockKeywordRepository;
    private final StockScoreAlertService stockScoreAlertService;

    @Transactional
    public void saveScoreAndKeyword(Integer stockId, COUNTRY country, int yesterdayScore,
        ScoreKeywordResponse scoreKeywordResponse) {

        Stock stock = stockRepository.findById(stockId)
            .orElseThrow(() -> new RuntimeException("Could not find stock"));

        int finalScore = scoreKeywordResponse.getFinalScore();
        Score newScore = Score.builder()
            .stockId(stock.getId())
            .date(LocalDate.now())
            .scoreKorea(country == COUNTRY.KOREA ? finalScore : 9999)
            .scoreNaver(finalScore)
            .scoreReddit(9999)
            .scoreOversea(country == COUNTRY.OVERSEA ? finalScore : 9999)
            .diff(finalScore - yesterdayScore)
            .build();

        newScore.setStock(stock);
        scoreRepository.save(newScore);

        // 점수 급변 알림 트리거 (절대 변화량 기준은 StockScoreAlertService 에서 판단)
        stockScoreAlertService.onScoreChanged(stock.getId(), yesterdayScore, finalScore);

        stockKeywordRepository.deleteByStock(stock);
        scoreKeywordResponse.getTopKeywords().forEach(keywordDto -> {
            Keyword newKeyword = Keyword.builder()
                .name(keywordDto.getWord())
                .frequency(keywordDto.getFreq())
                .build();

            keywordRepository.save(newKeyword);

            StockKeyword stockKeyword = StockKeyword.builder()
                .stock(stock)
                .keyword(newKeyword)
                .build();
            stockKeywordRepository.save(stockKeyword);
        });
    }
}
