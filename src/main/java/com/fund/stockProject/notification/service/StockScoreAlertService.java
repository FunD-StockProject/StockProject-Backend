package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.entity.OutboxEvent;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.OutboxRepository;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockScoreAlertService {
    private final NotificationRepository notificationRepo;
    private final OutboxRepository outboxRepo;
    private final NotificationService notificationService;
    private final PreferenceRepository preferenceRepo;

    private static final int THRESHOLD_ABS = 15;

    /**
     * 점수 변경 시 예약 알림 생성 (오전 9시 발송)
     */
    @Transactional
    public void onScoreChanged(Integer stockId, int oldScore, int newScore) {
        int delta = Math.abs(newScore - oldScore);
        if (delta < THRESHOLD_ABS) return;

        // 이 종목을 북마크하고 알림이 활성화된 사용자들 조회
        List<Preference> preferences = preferenceRepo.findByStockIdAndPreferenceType(stockId, PreferenceType.BOOKMARK);
        List<Preference> activePreferences = preferences.stream()
                .filter(p -> p.getNotificationEnabled())
                .toList();
        
        if (activePreferences.isEmpty()) return;

        // 오전 9시 발송 시간 계산
        Instant scheduledAt = calculateNextNineAM();

        for (Preference preference : activePreferences) {
            String title = preference.getStock().getSymbolName() + " 점수 급변: " + oldScore + " → " + newScore;
            String body = "북마크한 종목의 점수가 " + delta + "p 변했습니다.";

            notificationService.createScheduledStockNotification(
                preference.getUser(), 
                preference.getStock(), 
                NotificationType.SCORE_SPIKE,
                title, 
                body, 
                scheduledAt
            );
        }
    }

    /**
     * 오전 9시 발송 시간 계산
     */
    private Instant calculateNextNineAM() {
        LocalDate today = LocalDate.now();
        LocalTime nineAM = LocalTime.of(9, 0);
        
        // 오늘 오전 9시가 지났으면 내일 오전 9시로 설정
        if (LocalTime.now().isAfter(nineAM)) {
            today = today.plusDays(1);
        }
        
        return today.atTime(nineAM).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }

    /**
     * 오전 9시에 실행되는 일일 점수 급변 알림 발송
     */
    @Transactional
    public void sendDailyScoreAlerts() {
        // PENDING 상태이고 scheduledAt이 현재 시간 이전인 알림들을 발송
        List<OutboxEvent> pendingEvents = outboxRepo.findByStatusAndScheduledAtBefore(
            "PENDING", Instant.now());
        
        for (OutboxEvent event : pendingEvents) {
            // OutboxDispatcher에서 처리하도록 상태 업데이트
            event.setStatus("READY_TO_SEND");
        }
    }
}

