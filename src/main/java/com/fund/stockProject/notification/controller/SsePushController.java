package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.service.SsePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
class SsePushController {
    private final SsePushService sse;
    @GetMapping("/api/notifications/stream")
    public SseEmitter stream(@AuthenticationPrincipal(expression = "id") Integer userId) {
        return sse.register(userId);
    }
}
