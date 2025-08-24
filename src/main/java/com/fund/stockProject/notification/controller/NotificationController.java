package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * 사용자별 알림 목록 조회
     */
    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @AuthenticationPrincipal(expression = "id") Integer userId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 알림 읽음 처리
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal(expression = "id") Integer userId,
            @PathVariable Integer notificationId) {
        
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        // 본인의 알림인지 확인
        if (!notification.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
        
        return ResponseEntity.ok().build();
    }

    /**
     * 모든 알림 읽음 처리
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal(expression = "id") Integer userId) {
        
        notificationRepository.markAllAsReadByUserId(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @AuthenticationPrincipal(expression = "id") Integer userId) {
        
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(Map.of("unreadCount", (int) count));
    }

    /**
     * 특정 타입의 알림만 조회
     */
    @GetMapping("/type/{notificationType}")
    public ResponseEntity<Page<Notification>> getNotificationsByType(
            @AuthenticationPrincipal(expression = "id") Integer userId,
            @PathVariable NotificationType notificationType,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<Notification> notifications = notificationRepository.findByUserIdAndNotificationTypeOrderByCreatedAtDesc(
                userId, notificationType, pageable);
        return ResponseEntity.ok(notifications);
    }


}
