package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.COUNTRY;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScoreUpdateScheduler {

    private final ScoreService scoreService;

    @Scheduled(cron = "10 0 0 * * ?", zone = "Asia/Seoul") // 0시 경에 실행
    public void processScores() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = LocalDate.now().minusDays(1); // 어제 날짜 계산

        // 오늘 데이터가 없는 어제 날짜의 데이터를 조회
        List<Score> scores = scoreService.findScoresByDate(yesterday, today);

        for (Score score : scores) {
            try {
                // COUNTRY를 설정하고 updateScore 실행
                COUNTRY country = determineCountry(score);
                if (country == COUNTRY.KOREA) {
                    scoreService.updateScore(score.getStockId(), country, score.getScoreKorea());
                }
                else if (country == COUNTRY.OVERSEA) {
                    scoreService.updateScore(score.getStockId(), country, score.getScoreOversea());
                }
            } catch (Exception e) {
                System.err.println("Error processing score " + score.getStockId() + " - " + e.getMessage());
            }
        }
    }

    private COUNTRY determineCountry(Score score) {
        if (score.getScoreKorea() == 9999) {
            return COUNTRY.OVERSEA;
        } else if (score.getScoreOversea() == 9999) {
            return COUNTRY.KOREA;
        } else {
            throw new IllegalArgumentException("Invalid score data: stock_id=" + score.getStockId());
        }
    }
}