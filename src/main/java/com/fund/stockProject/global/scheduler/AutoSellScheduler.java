package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.experiment.entity.Experiment;
import com.fund.stockProject.experiment.service.ExperimentService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoSellScheduler {

    private ExperimentService experimentService;

    /**
     * 모의투자 자동매매 스케줄러
     */
    @Scheduled(cron = "0 30 23 ? * MON-FRI", zone = "Asia/Seoul")
    public void processAutoSell() {
        LocalDate today = LocalDate.now();

        // 모의 매수 5일차 종목 조회
        final List<Experiment> experimentsAfter5BusinessDays = experimentService.findExperimentsAfter5BusinessDays();

        for (Experiment experiment : experimentsAfter5BusinessDays) {
            try {
                experimentService.updateExperiment(experiment);
            } catch (Exception e) {
                System.err.println("Error processing autoSell " + experiment.getId() + " - " + e.getMessage());
            }
        }

    }

    @Scheduled(cron = "0 0 18 * * ?", zone = "Asia/Seoul")
    public void processProgressExperiment() {
        LocalDate today = LocalDate.now();

        // 모의 매수 5일차 미만의 실험 진행중인 종목 조회
        final List<Experiment> experimentsPrevious5BusinessDays = experimentService.findExperimentsPrevious5BusinessDays();

        // 영업일 기준 데이터 저장
        for (Experiment experiment : experimentsPrevious5BusinessDays) {
            try {
                experimentService.saveExperimentTradeItem(experiment);
            } catch (Exception e) {
                System.err.println("Error processing Progress Experiment " + experiment.getId() + " - " + e.getMessage());
            }
        }

    }
}
