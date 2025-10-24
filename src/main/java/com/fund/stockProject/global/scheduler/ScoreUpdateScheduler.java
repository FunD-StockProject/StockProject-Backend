package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.score.service.ScoreBatchService;
import com.fund.stockProject.stock.domain.COUNTRY;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreUpdateScheduler {

    private final ScoreBatchService scoreBatchService;

    /**
     * 해외 점수&키워드 업데이트 스케줄러
     */
    @Scheduled(cron = "0 0 6 * * ?", zone = "Asia/Seoul") // 6시에 실행
    public void processScoresOversea() {
        scoreBatchService.runCountryBatch(COUNTRY.OVERSEA);
    }

    /**
     * 국내 점수&키워드 업데이트 스케줄러
     */
    @Scheduled(cron = "0 0 17 * * ?", zone = "Asia/Seoul") // 17시에 실행
    public void processScoresKorea() {
        scoreBatchService.runCountryBatch(COUNTRY.KOREA);
    }

    /**
     * 공포지수, 지수 업데이트 스케줄러
     */
    @Scheduled(cron = "0 5 7 * * ?", zone = "Asia/Seoul") // 매일 7시 5분 실행
    public void processIndexScores() {
        scoreBatchService.runIndexBatch();
    }
}