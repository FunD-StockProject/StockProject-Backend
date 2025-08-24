package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.entity.Notification;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SsePushService {
    private final Map<Integer, CopyOnWriteArrayList<SseEmitter>> sessions = new ConcurrentHashMap<>();
    public SseEmitter register(Integer userId) {
        SseEmitter em = new SseEmitter(0L);
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(em);
        em.onCompletion(() -> sessions.getOrDefault(userId, new CopyOnWriteArrayList<>()).remove(em));
        em.onTimeout(() -> sessions.getOrDefault(userId, new CopyOnWriteArrayList<>()).remove(em));
        return em;
    }
    public void pushToUser(Integer userId, Notification n) {
        for (SseEmitter em : sessions.getOrDefault(userId, new CopyOnWriteArrayList<>())) {
            try { em.send(SseEmitter.event().name("alert").data(n)); } catch (IOException ignore) {}
        }
    }
}
