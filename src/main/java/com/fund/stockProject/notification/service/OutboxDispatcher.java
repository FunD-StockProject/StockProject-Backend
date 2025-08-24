package com.fund.stockProject.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.entity.OutboxEvent;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.OutboxRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OutboxDispatcher {
    private final OutboxRepository outboxRepo;
    private final NotificationRepository notificationRepo;
    private final SsePushService ssePushService;  // 웹/웹뷰 실시간
    private final ObjectMapper om;
    
    // FCM 서비스는 선택적으로 주입 (없을 수 있음)
    private final FcmPushService fcmPushService;
    
    public OutboxDispatcher(
            OutboxRepository outboxRepo,
            NotificationRepository notificationRepo,
            SsePushService ssePushService,
            ObjectMapper om,
            @Autowired(required = false) FcmPushService fcmPushService) {
        this.outboxRepo = outboxRepo;
        this.notificationRepo = notificationRepo;
        this.ssePushService = ssePushService;
        this.om = om;
        this.fcmPushService = fcmPushService;
    }

    /**
     * 즉시 발송 처리 (1.5초마다)
     */
    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void dispatchImmediate() {
        // 즉시 발송할 이벤트들 (scheduledAt이 null이거나 현재 시간 이전)
        var batch = outboxRepo.findReadyToProcessInStatuses(
                List.of("PENDING", "READY_TO_SEND"),
                Instant.now(),
                PageRequest.of(0, 100)
        );

        for (OutboxEvent e : batch.getContent()) {
            if (!"ALERT_CREATED".equals(e.getType())) continue;
            
            processEvent(e);
        }
    }

    /**
     * 재시도 이벤트 처리 (5분마다)
     */
    @Scheduled(fixedDelay = 300000) // 5분
    @Transactional
    public void dispatchRetry() {
        List<OutboxEvent> retryEvents = outboxRepo.findRetryableEvents("RETRY", Instant.now());
        
        for (OutboxEvent e : retryEvents) {
            if (!"ALERT_CREATED".equals(e.getType())) continue;
            
            processEvent(e);
        }
    }

    /**
     * 이벤트 처리 공통 로직
     */
    private void processEvent(OutboxEvent e) {
        try {
            Map<String, Object> payload = om.readValue(e.getPayload(), new TypeReference<>(){});
            Integer nId = Integer.valueOf(payload.get("notificationId").toString());
            Integer userId = Integer.valueOf(payload.get("userId").toString());
            Notification n = notificationRepo.findById(nId).orElseThrow();

            // SSE 푸시 (웹/웹뷰)
            ssePushService.pushToUser(userId, n);
            
            // FCM 푸시 (모바일) - FCM이 활성화된 경우에만
            if (fcmPushService != null) {
                boolean quiet = Boolean.TRUE.equals(payload.get("quietPush"));
                Map<String, String> data = Map.of(
                        "notificationId", n.getId().toString(),
                        "stockId", n.getStock() != null ? n.getStock().getId().toString() : "",
                        "type", n.getNotificationType().name()
                );

                if (quiet) {
                    fcmPushService.sendSilent(userId, data);
                } else {
                    fcmPushService.sendAlert(userId, n.getTitle(), n.getBody(), data);
                }
            }

            e.setStatus("PROCESSED");
            log.info("Notification sent successfully: userId={}, notificationId={}", userId, nId);
            
        } catch (Exception ex) {
            handleError(e, ex);
        }
    }

    /**
     * 에러 처리 및 재시도 로직
     */
    private void handleError(OutboxEvent e, Exception ex) {
        int retryCount = e.getRetryCount() + 1;
        e.setRetryCount(retryCount);
        
        // 지수 백오프: 1분, 2분, 4분, 8분, 16분, 32분, 64분, 128분, 256분, 300분(최대)
        long backoffSec = Math.min(300, 1L << Math.min(10, retryCount));
        e.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
        e.setStatus("RETRY");
        
        log.warn("Notification dispatch failed, will retry: eventId={}, retryCount={}, nextAttempt={}, error={}", 
                e.getId(), retryCount, e.getNextAttemptAt(), ex.getMessage());
    }
}
