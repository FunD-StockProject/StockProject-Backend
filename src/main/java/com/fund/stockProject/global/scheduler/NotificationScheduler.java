package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.notification.service.NotificationService;
import com.fund.stockProject.notification.service.StockScoreAlertService;
import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final StockScoreAlertService stockScoreAlertService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

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

    /**
     * 오전 9시 5분에 일일 요약 알림 발송
     */
    @Scheduled(cron = "0 5 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void sendDailySummaryNotifications() {
        log.info("Starting daily summary notifications at 9:05 AM");
        
        try {
            // 모든 활성 사용자에게 일일 요약 알림 발송
            List<User> activeUsers = userRepository.findByIsActiveTrue();
            
            String title = "오늘의 주식 시장 요약";
            String body = "어제의 주요 종목 점수 변화와 시장 동향을 확인해보세요.";
            
            Instant scheduledAt = Instant.now(); // 즉시 발송
            
            notificationService.createBulkNotification(
                activeUsers, 
                NotificationType.DAILY_SUMMARY, 
                title, 
                body, 
                scheduledAt
            );
            
            log.info("Daily summary notifications sent to {} users", activeUsers.size());
        } catch (Exception e) {
            log.error("Failed to send daily summary notifications", e);
        }
    }

    /**
     * 오전 9시 10분에 장 시작 알림 발송
     */
    @Scheduled(cron = "0 10 9 * * 1-5", zone = "Asia/Seoul") // 평일만
    @Transactional
    public void sendMarketOpenNotifications() {
        log.info("Starting market open notifications at 9:10 AM");
        
        try {
            List<User> activeUsers = userRepository.findByIsActiveTrue();
            
            String title = "장 시작 알림";
            String body = "오늘도 좋은 하루 되세요! 주식 시장이 열렸습니다.";
            
            Instant scheduledAt = Instant.now();
            
            notificationService.createBulkNotification(
                activeUsers, 
                NotificationType.MARKET_OPEN, 
                title, 
                body, 
                scheduledAt
            );
            
            log.info("Market open notifications sent to {} users", activeUsers.size());
        } catch (Exception e) {
            log.error("Failed to send market open notifications", e);
        }
    }

    /**
     * 오후 3시 30분에 장 마감 알림 발송
     */
    @Scheduled(cron = "0 30 15 * * 1-5", zone = "Asia/Seoul") // 평일만
    @Transactional
    public void sendMarketCloseNotifications() {
        log.info("Starting market close notifications at 3:30 PM");
        
        try {
            List<User> activeUsers = userRepository.findByIsActiveTrue();
            
            String title = "장 마감 알림";
            String body = "오늘 하루도 수고하셨습니다. 내일도 좋은 하루 되세요!";
            
            Instant scheduledAt = Instant.now();
            
            notificationService.createBulkNotification(
                activeUsers, 
                NotificationType.MARKET_CLOSE, 
                title, 
                body, 
                scheduledAt
            );
            
            log.info("Market close notifications sent to {} users", activeUsers.size());
        } catch (Exception e) {
            log.error("Failed to send market close notifications", e);
        }
    }
}
