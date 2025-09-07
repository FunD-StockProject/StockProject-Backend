package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.experiment.entity.ExperimentItem;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.stock.domain.COUNTRY;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoSellingScheduler {

    private ExperimentService experimentService;

    /**
     * 모의투자 자동매매 스케줄러
     */
    @Scheduled(cron = "0 30 23 * * ?", zone = "Asia/Seoul") // 6시에 실행
    public void processAutoSell() {
        LocalDate today = LocalDate.now();

        // 모의 매수 5일차 종목 조회
        final List<ExperimentItem> experimentItemsAfter5BusinessDays = experimentService.findExperimentItemAfter5BusinessDays();

        for (ExperimentItem experimentItem : experimentItemsAfter5BusinessDays) {
            try {
                experimentService.updateAutoSellStockStatus(experimentItem);
            } catch (Exception e) {
                System.err.println("Error processing autoSell " + experimentItem.getId() + " - " + e.getMessage());
            }
        }

    }
}
