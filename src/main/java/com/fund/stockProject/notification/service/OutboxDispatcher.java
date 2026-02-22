package com.fund.stockProject.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.entity.OutboxEvent;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * 즉시 알림 발송 처리
     */
    @Scheduled(fixedDelay = 60000) // 1분
    public void dispatchImmediate() {
        try {
            var batch = outboxRepo.findReadyImmediateEvents(PageRequest.of(0, 100));

            log.debug("Found {} immediate events to process", batch.getContent().size());

            for (OutboxEvent e : batch.getContent()) {
                if (!"ALERT_CREATED".equals(e.getType())) continue;

                processEvent(e);
            }
        } catch (Exception e) {
            log.error("Error in dispatchImmediate scheduler", e);
        }
    }

    /**
     * 예약 알림 발송 처리 (9시대 매분)
     */
    @Scheduled(cron = "0 * 9 * * *", zone = "Asia/Seoul")
    public void dispatchScheduled() {
        try {
            var batch = outboxRepo.findReadyScheduledEvents(Instant.now(), PageRequest.of(0, 200));

            log.debug("Found {} scheduled events to process", batch.getContent().size());

            for (OutboxEvent e : batch.getContent()) {
                if (!"ALERT_CREATED".equals(e.getType())) continue;

                processEvent(e);
            }
        } catch (Exception e) {
            log.error("Error in dispatchScheduled scheduler", e);
        }
    }

    /**
     * 재시도 이벤트 처리 (5분마다)
     */
    @Scheduled(fixedDelay = 300000) // 5분
    public void dispatchRetry() {
        try {
            List<OutboxEvent> retryEvents = outboxRepo.findRetryableEvents("RETRY", Instant.now());

            log.debug("Found {} events to retry", retryEvents.size());

            for (OutboxEvent e : retryEvents) {
                if (!"ALERT_CREATED".equals(e.getType())) continue;

                processEvent(e);
            }
        } catch (Exception e) {
            log.error("Error in dispatchRetry scheduler", e);
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
            outboxRepo.save(e);
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
        
        // 지수 백오프(분 단위): 1분, 2분, 4분 ... 최대 300분
        final long backoffMinutes = Math.min(300L, 1L << Math.min(10, Math.max(0, retryCount - 1)));
        long backoffSec = backoffMinutes * 60L;
        e.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
        e.setStatus("RETRY");
        outboxRepo.save(e);
        
        log.warn("Notification dispatch failed, will retry: eventId={}, retryCount={}, nextAttempt={}, error={}", 
                e.getId(), retryCount, e.getNextAttemptAt(), ex.getMessage());
    }

    /**
     * 오래된 ���리 완료 이벤트 정리 (매일 새벽 3시)
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Seoul")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupOldEvents() {
        try {
            // 7일 이상 된 PROCESSED 이벤트 삭제
            Instant cutoffDate = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
            List<OutboxEvent> oldEvents = outboxRepo.findByStatusAndCreatedAtBefore("PROCESSED", cutoffDate);

            if (!oldEvents.isEmpty()) {
                outboxRepo.deleteAll(oldEvents);
                log.info("Cleaned up {} old outbox events", oldEvents.size());
            }

            // 30일 이상 재시도에 실패한 이벤트들을 FAILED로 변경
            Instant veryOldCutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
            List<OutboxEvent> failedEvents = outboxRepo.findByStatusAndCreatedAtBefore("RETRY", veryOldCutoff);

            if (!failedEvents.isEmpty()) {
                failedEvents.forEach(event -> event.setStatus("FAILED"));
                outboxRepo.saveAll(failedEvents);
                log.warn("Marked {} old retry events as FAILED", failedEvents.size());
            }

        } catch (Exception e) {
            log.error("Error during outbox cleanup", e);
            // 정리 작업 실패는 시스템에 치명적이지 않으므로 예외를 삼킴
        }
    }
}
