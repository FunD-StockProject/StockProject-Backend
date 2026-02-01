package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.score.service.ScoreBatchService;
import com.fund.stockProject.stock.service.SectorScoreSnapshotService;
import com.fund.stockProject.stock.domain.COUNTRY;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreUpdateScheduler {

    private final ScoreBatchService scoreBatchService;
    private final SectorScoreSnapshotService sectorScoreSnapshotService;

    /**
     * 해외 점수&키워드 업데이트 스케줄러
     */
    @Scheduled(cron = "0 0 6 * * ?", zone = "Asia/Seoul") // 6시에 실행
    public void processScoresOversea() {
        log.info("Starting oversea score batch scheduler");
        try {
            scoreBatchService.runCountryBatch(COUNTRY.OVERSEA);
            sectorScoreSnapshotService.saveDailySnapshot(COUNTRY.OVERSEA, java.time.LocalDate.now());
            log.info("Oversea score batch scheduler completed successfully");
        } catch (Exception e) {
            log.error("Oversea score batch scheduler failed", e);
            throw e;
        }
    }

    /**
     * 국내 점수&키워드 업데이트 스케줄러
     */
    @Scheduled(cron = "0 0 17 * * ?", zone = "Asia/Seoul") // 17시에 실행
    public void processScoresKorea() {
        log.info("Starting korea score batch scheduler");
        try {
            scoreBatchService.runCountryBatch(COUNTRY.KOREA);
            sectorScoreSnapshotService.saveDailySnapshot(COUNTRY.KOREA, java.time.LocalDate.now());
            log.info("Korea score batch scheduler completed successfully");
        } catch (Exception e) {
            log.error("Korea score batch scheduler failed", e);
            throw e;
        }
    }

    /**
     * 공포지수, 지수 업데이트 스케줄러
     */
    @Scheduled(cron = "0 5 7 * * ?", zone = "Asia/Seoul") // 매일 7시 5분 실행
    public void processIndexScores() {
        log.info("Starting index score batch scheduler");
        try {
            scoreBatchService.runIndexBatch();
            log.info("Index score batch scheduler completed successfully");
        } catch (Exception e) {
            log.error("Index score batch scheduler failed", e);
            throw e;
        }
    }
}
