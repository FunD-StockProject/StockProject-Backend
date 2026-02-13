package com.fund.stockProject.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.entity.OutboxEvent;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.OutboxRepository;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockScoreAlertService {
    private final NotificationRepository notificationRepo;
    private final OutboxRepository outboxRepo;
    private final NotificationService notificationService;
    private final PreferenceRepository preferenceRepo;
    private final ObjectMapper objectMapper;

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
                oldScore,
                newScore,
                delta,
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

        if (pendingEvents.isEmpty()) {
            return;
        }

        Map<Integer, List<OutboxEvent>> scoreSpikeEventsByUser = new HashMap<>();
        int readyCount = 0;
        int suppressedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            Map<String, Object> payload = parsePayload(event.getPayload());
            if (payload == null) {
                event.setStatus("READY_TO_SEND");
                readyCount++;
                continue;
            }

            String type = String.valueOf(payload.get("type"));
            Integer userId = toInteger(payload.get("userId"));

            if (NotificationType.SCORE_SPIKE.name().equals(type) && userId != null) {
                scoreSpikeEventsByUser.computeIfAbsent(userId, key -> new ArrayList<>()).add(event);
                continue;
            }

            event.setStatus("READY_TO_SEND");
            readyCount++;
        }

        for (Map.Entry<Integer, List<OutboxEvent>> entry : scoreSpikeEventsByUser.entrySet()) {
            List<OutboxEvent> userEvents = entry.getValue();
            if (userEvents.isEmpty()) {
                continue;
            }

            OutboxEvent representative = userEvents.get(0);
            representative.setStatus("READY_TO_SEND");
            readyCount++;

            if (userEvents.size() > 1) {
                updateRepresentativeMessage(representative, userEvents.size());
                for (int i = 1; i < userEvents.size(); i++) {
                    userEvents.get(i).setStatus("PROCESSED");
                    suppressedCount++;
                }
            }
        }

        log.info("Daily score alerts prepared: totalPending={}, readyToSend={}, suppressed={}",
            pendingEvents.size(), readyCount, suppressedCount);
    }

    private void updateRepresentativeMessage(OutboxEvent representative, int totalCount) {
        Map<String, Object> payload = parsePayload(representative.getPayload());
        if (payload == null) {
            return;
        }

        Integer notificationId = toInteger(payload.get("notificationId"));
        if (notificationId == null) {
            return;
        }

        notificationRepo.findById(notificationId).ifPresent(notification -> {
            notification.setTitle("북마크 종목 점수 급변 알림");
            notification.setBody("북마크한 종목 " + totalCount + "개의 점수가 크게 변했습니다.");
            notificationRepo.save(notification);
        });
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse outbox payload: {}", e.getMessage());
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
