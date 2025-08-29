package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SsePushService {
    private final Map<Integer, CopyOnWriteArrayList<SseEmitter>> sessions = new ConcurrentHashMap<>();

    public SseEmitter register(Integer userId) {
        // 30분 타임아웃 설정 (무제한 연결 방지)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분

        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결 정리 로직 강화
        emitter.onCompletion(() -> {
            removeEmitter(userId, emitter);
            log.debug("SSE connection completed for user: {}", userId);
        });

        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            log.debug("SSE connection timed out for user: {}", userId);
        });

        emitter.onError((throwable) -> {
            removeEmitter(userId, emitter);
            log.warn("SSE connection error for user: {}, error: {}", userId, throwable.getMessage());
        });

        // 초기 연결 확인 메시지 전송
        try {
            emitter.send(SseEmitter.event().name("connected").data("Connection established"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
            log.warn("Failed to send initial SSE message for user: {}", userId);
        }

        return emitter;
    }

    public void pushToUser(Integer userId, Notification n) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = sessions.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        // 실패한 emitter들을 추적해서 제거
        userEmitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("alert").data(n));
                return false; // 성공하면 제거하지 않음
            } catch (IOException e) {
                log.debug("Failed to send SSE message to user: {}, removing emitter", userId);
                return true; // 실패하면 제거
            }
        });

        // 빈 리스트는 Map에서 제거
        if (userEmitters.isEmpty()) {
            sessions.remove(userId);
        }
    }

    private void removeEmitter(Integer userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = sessions.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    // 정리 작업을 위한 메소드 추가
    public void disconnectUser(Integer userId) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = sessions.remove(userId);
        if (userEmitters != null) {
            userEmitters.forEach(emitter -> {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Error completing SSE emitter for user: {}", userId);
                }
            });
        }
    }

    // 현재 연결 상태 확인을 위한 메소드
    public int getActiveConnectionsCount() {
        return sessions.values().stream()
                .mapToInt(CopyOnWriteArrayList::size)
                .sum();
    }
}
