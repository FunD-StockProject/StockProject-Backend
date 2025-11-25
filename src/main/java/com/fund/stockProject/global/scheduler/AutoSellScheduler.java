package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.experiment.entity.Experiment;
import com.fund.stockProject.experiment.service.ExperimentService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSellScheduler {

    private final ExperimentService experimentService;

    /**
     * 모의투자 자동매매 스케줄러
     */
    @Scheduled(cron = "0 30 23 ? * MON-FRI", zone = "Asia/Seoul")
    public void processAutoSell() {
        log.info("Starting auto-sell scheduler");
        LocalDate today = LocalDate.now();

        // 모의 매수 5일차 종목 조회
        final List<Experiment> experimentsAfter5BusinessDays = experimentService.findExperimentsAfter5BusinessDays();
        log.info("Found {} experiments for auto-sell", experimentsAfter5BusinessDays.size());

        int successCount = 0;
        int failureCount = 0;
        
        for (Experiment experiment : experimentsAfter5BusinessDays) {
            try {
                boolean success = experimentService.updateExperiment(experiment);
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (Exception e) {
                failureCount++;
                log.error("Error processing auto-sell for experiment - experimentId: {}", experiment.getId(), e);
            }
        }

        log.info("Auto-sell scheduler completed - Total: {}, Success: {}, Failed: {}", 
                experimentsAfter5BusinessDays.size(), successCount, failureCount);
    }

    @Scheduled(cron = "0 0 18 * * ?", zone = "Asia/Seoul")
    public void processProgressExperiment() {
        log.info("Starting progress experiment scheduler");
        LocalDate today = LocalDate.now();

        // 모의 매수 5일차 미만의 실험 진행중인 종목 조회
        final List<Experiment> experimentsPrevious5BusinessDays = experimentService.findExperimentsPrevious5BusinessDays();
        log.info("Found {} experiments in progress", experimentsPrevious5BusinessDays.size());

        int successCount = 0;
        int failureCount = 0;

        // 영업일 기준 데이터 저장
        for (Experiment experiment : experimentsPrevious5BusinessDays) {
            try {
                experimentService.saveExperimentTradeItem(experiment);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Error processing progress experiment - experimentId: {}", experiment.getId(), e);
            }
        }

        log.info("Progress experiment scheduler completed - Total: {}, Success: {}, Failed: {}", 
                experimentsPrevious5BusinessDays.size(), successCount, failureCount);
    }
}
