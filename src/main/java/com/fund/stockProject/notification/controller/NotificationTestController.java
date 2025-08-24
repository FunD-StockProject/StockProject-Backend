package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.service.NotificationService;
import com.fund.stockProject.notification.service.StockScoreAlertService;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/test/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationTestController {

    private final NotificationService notificationService;
    private final StockScoreAlertService stockScoreAlertService;
    private final UserRepository userRepository;

    /**
     * 즉시 알림 테스트
     */
    @PostMapping("/test-immediate")
    public ResponseEntity<Map<String, String>> testImmediateNotification(
            @AuthenticationPrincipal(expression = "id") Integer userId) {
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            notificationService.createImmediateNotification(
                user, 
                NotificationType.SYSTEM_MAINTENANCE,
                "테스트 알림",
                "이것은 테스트 알림입니다. 알림 시스템이 정상 작동하고 있습니다!"
            );

            log.info("Test notification sent to user: {}", userId);
            return ResponseEntity.ok(Map.of("message", "Test notification sent successfully"));
            
        } catch (Exception e) {
            log.error("Failed to send test notification", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send test notification: " + e.getMessage()));
        }
    }

    /**
     * 예약 알림 테스트 (1분 후 발송)
     */
    @PostMapping("/test-scheduled")
    public ResponseEntity<Map<String, String>> testScheduledNotification(
            @AuthenticationPrincipal(expression = "id") Integer userId) {
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 1분 후 발송
            Instant scheduledAt = Instant.now().plusSeconds(60);

            notificationService.createScheduledNotification(
                user, 
                NotificationType.SYSTEM_MAINTENANCE,
                "예약 테스트 알림",
                "이것은 1분 후에 발송되는 예약 테스트 알림입니다!",
                scheduledAt
            );

            log.info("Scheduled test notification created for user: {}, scheduled at: {}", userId, scheduledAt);
            return ResponseEntity.ok(Map.of("message", "Scheduled test notification created successfully"));
            
        } catch (Exception e) {
            log.error("Failed to create scheduled test notification", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to create scheduled test notification: " + e.getMessage()));
        }
    }

    /**
     * 점수 급변 알림 테스트
     */
    @PostMapping("/test-score-alert")
    public ResponseEntity<Map<String, String>> testScoreAlert(
            @RequestParam Integer stockId,
            @RequestParam(defaultValue = "50") Integer oldScore,
            @RequestParam(defaultValue = "80") Integer newScore) {
        
        try {
            // StockScoreAlertService의 onScoreChanged 메서드 직접 호출
            stockScoreAlertService.onScoreChanged(stockId, oldScore, newScore);
            
            log.info("Test score alert triggered: stockId={}, oldScore={}, newScore={}", stockId, oldScore, newScore);
            return ResponseEntity.ok(Map.of("message", "Score alert test triggered successfully"));
            
        } catch (Exception e) {
            log.error("Failed to trigger score alert test", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to trigger score alert test: " + e.getMessage()));
        }
    }

    /**
     * 알림 시스템 상태 확인
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getNotificationStatus(
            @AuthenticationPrincipal(expression = "id") Integer userId) {
        
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> status = Map.of(
                "userId", userId,
                "userEmail", user.getEmail(),
                "notificationSystem", "ACTIVE",
                "timestamp", Instant.now().toString(),
                "message", "Notification system is running"
            );

            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Failed to get notification status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get notification status: " + e.getMessage()));
        }
    }
}
