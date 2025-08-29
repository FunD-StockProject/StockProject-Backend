package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.notification.service.StockScoreAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final StockScoreAlertService stockScoreAlertService;

    /**
     * 오전 9시에 점수 급변 알림 발송
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDailyScoreAlerts() {
        log.info("Starting daily score alerts dispatch at 9 AM");
        
        try {
            stockScoreAlertService.sendDailyScoreAlerts();
            log.info("Daily score alerts dispatch completed");
        } catch (Exception e) {
            log.error("Failed to dispatch daily score alerts", e);
        }
    }
}
