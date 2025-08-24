package com.fund.stockProject.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.entity.OutboxEvent;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.OutboxRepository;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 즉시 발송되는 알림 생성
     */
    @Transactional
    public void createImmediateNotification(User user, NotificationType type, String title, String body) {
        createNotification(user, null, type, title, body, null, null, null, null);
    }

    /**
     * 즉시 발송되는 주식 관련 알림 생성
     */
    @Transactional
    public void createImmediateStockNotification(User user, Stock stock, NotificationType type, 
                                               String title, String body, Integer oldScore, 
                                               Integer newScore, Integer changeAbs) {
        createNotification(user, stock, type, title, body, oldScore, newScore, changeAbs, null);
    }

    /**
     * 예약 발송되는 알림 생성
     */
    @Transactional
    public void createScheduledNotification(User user, NotificationType type, String title, 
                                          String body, Instant scheduledAt) {
        createNotification(user, null, type, title, body, null, null, null, scheduledAt);
    }

    /**
     * 예약 발송되는 주식 관련 알림 생성
     */
    @Transactional
    public void createScheduledStockNotification(User user, Stock stock, NotificationType type,
                                               String title, String body, Instant scheduledAt) {
        createNotification(user, stock, type, title, body, null, null, null, scheduledAt);
    }

    /**
     * 여러 사용자에게 일괄 알림 생성
     */
    @Transactional
    public void createBulkNotification(List<User> users, NotificationType type, String title, 
                                     String body, Instant scheduledAt) {
        for (User user : users) {
            createNotification(user, null, type, title, body, null, null, null, scheduledAt);
        }
    }

    /**
     * 알림 생성 공통 메서드
     */
    private void createNotification(User user, Stock stock, NotificationType type, String title, 
                                  String body, Integer oldScore, Integer newScore, Integer changeAbs, 
                                  Instant scheduledAt) {
        
        Notification notification = Notification.builder()
                .user(user)
                .stock(stock)
                .notificationType(type)
                .oldScore(oldScore)
                .newScore(newScore)
                .changeAbs(changeAbs)
                .title(title)
                .body(body)
                .build();

        notification = notificationRepository.save(notification);

        // OutboxEvent 생성
        Map<String, Object> payload = Map.of(
                "notificationId", notification.getId(),
                "userId", user.getId(),
                "stockId", stock != null ? stock.getId() : null,
                "type", type.name(),
                "scheduledAt", scheduledAt != null ? scheduledAt.toString() : null
        );

        OutboxEvent.OutboxEventBuilder outboxBuilder = OutboxEvent.builder()
                .type("ALERT_CREATED")
                .payload(writeJson(payload));

        if (scheduledAt != null) {
            outboxBuilder.scheduledAt(scheduledAt);
        }

        outboxRepository.save(outboxBuilder.build());
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
