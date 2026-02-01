package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.dto.NotificationResponse;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.service.FcmPushService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "알림 (Notification)", description = "사용자 알림 조회/읽음 처리 및 테스트 API")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmPushService fcmPushService;

    /**
     * 사용자별 알림 목록 조회
     */
    @GetMapping
    @Operation(summary = "알림 목록 페이지 조회", description = "인증된 사용자의 알림 목록을 생성일 내림차순으로 페이지네이션하여 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = NotificationResponse.class)))
    })
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails,
            @PageableDefault(size = 20) @Parameter(description = "페이지네이션 정보(page, size, sort)") Pageable pageable) {

        Integer userId = userDetails.getUser().getId();
        Page<Notification> notifications = notificationRepository.findValidByUserIdOrderByCreatedAtDesc(
                userId, NotificationType.SCORE_SPIKE, pageable);

        Page<NotificationResponse> response = notifications.map(NotificationResponse::fromEntity);
        return ResponseEntity.ok(response);
    }

    /**
     * 알림 읽음 처리
     */
    @PatchMapping("/read/{notificationId}")
    @Operation(summary = "단일 알림 읽음 처리", description = "알림 ID에 해당하는 사용자의 알림을 읽음 처리하고 갱신된 알림 데이터를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "읽음 처리 성공", content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 소유 알림 아님"),
            @ApiResponse(responseCode = "404", description = "알림 없음")
    })
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "읽음 처리할 알림 ID", example = "123") @PathVariable Integer notificationId) {

        Integer userId = userDetails.getUser().getId();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        // 본인의 알림인지 확인
        if (!notification.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }

        return ResponseEntity.ok(NotificationResponse.fromEntity(notification));
    }

    /**
     * 모든 알림 읽음 처리
     */
    @PatchMapping("/read-all")
    @Operation(summary = "모든 알림 읽음 처리", description = "현재 사용자 알림 중 읽지 않은 모든 항목을 일괄 읽음 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "읽음 처리 완료", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails) {

        Integer userId = userDetails.getUser().getId();
        notificationRepository.markAllAsReadByUserId(userId);
        long unreadAfter = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(Map.of(
                "message", "All notifications marked as read",
                "unreadCount", unreadAfter
        ));
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 개수", description = "현재 사용자 기준 읽지 않은 알림 건수를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails) {

        Integer userId = userDetails.getUser().getId();
        long count = notificationRepository.countValidByUserIdAndIsReadFalse(
                userId, NotificationType.SCORE_SPIKE);
        return ResponseEntity.ok(Map.of("unreadCount", (int) count));
    }

    /**
     * 특정 타입의 알림만 조회
     */
    @GetMapping("/type/{notificationType}")
    @Operation(summary = "타입별 알림 목록", description = "지정된 알림 타입(NotificationType)에 해당하는 알림만 필터링하여 페이지로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 타입 입력")
    })
    public ResponseEntity<Page<NotificationResponse>> getNotificationsByType(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails,
            @Parameter(description = "알림 타입", example = "SCORE_SPIKE") @PathVariable NotificationType notificationType,
            @PageableDefault(size = 20) @Parameter(description = "페이지네이션 정보") Pageable pageable) {

        Integer userId = userDetails.getUser().getId();
        Page<Notification> notifications = notificationRepository.findValidByUserIdAndNotificationTypeOrderByCreatedAtDesc(
                userId, notificationType, NotificationType.SCORE_SPIKE, pageable);

        Page<NotificationResponse> response = notifications.map(NotificationResponse::fromEntity);
        return ResponseEntity.ok(response);
    }

    /**
     * 테스트 알림 생성
     */
    @PostMapping("/test")
    @Operation(summary = "테스트 알림 생성", description = "개발/테스트 용도로 임의의 알림 한 건을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공", content = @Content(schema = @Schema(implementation = NotificationResponse.class)))
    })
    public ResponseEntity<NotificationResponse> createTestNotification(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails) {

        Integer userId = userDetails.getUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification testNotification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.SCORE_SPIKE)
                .title("테스트 알림")
                .body("이것은 테스트 알림입니다. " + System.currentTimeMillis())
                .isRead(false)
                .build();

        Notification savedNotification = notificationRepository.save(testNotification);
        return ResponseEntity.ok(NotificationResponse.fromEntity(savedNotification));
    }

    /**
     * FCM 푸시 알림 테스트
     */
    @PostMapping("/test-fcm")
    @Operation(summary = "FCM 푸시 테스트", description = "실시간 푸시(Foreground/Notification) 테스트 알림을 발송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공"),
            @ApiResponse(responseCode = "500", description = "발송 실패")
    })
    public ResponseEntity<Map<String, String>> testFcmNotification(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails) {

        try {
            Integer userId = userDetails.getUser().getId();

            // FCM 테스트 알림 발송
            fcmPushService.sendAlert(
                    userId,
                    "FCM 테스트 알림",
                    "이것은 FCM 푸시 알림 테스트입니다! " + System.currentTimeMillis(),
                    Map.of("type", "test", "timestamp", String.valueOf(System.currentTimeMillis()))
            );

            return ResponseEntity.ok(Map.of("message", "FCM test notification sent successfully"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send FCM notification: " + e.getMessage()));
        }
    }

    /**
     * FCM 사일런트 알림 테스트
     */
    @PostMapping("/test-fcm-silent")
    @Operation(summary = "FCM 사일런트 테스트", description = "앱 백그라운드 데이터 갱신 등을 위한 사일런트 데이터 메시지를 발송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공"),
            @ApiResponse(responseCode = "500", description = "발송 실패")
    })
    public ResponseEntity<Map<String, String>> testFcmSilentNotification(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails) {

        try {
            Integer userId = userDetails.getUser().getId();

            // FCM 사일런트 알림 발송 (백그라운드 데이터 갱신용)
            fcmPushService.sendSilent(
                    userId,
                    Map.of("type", "silent_test", "action", "refresh_data", "timestamp", String.valueOf(System.currentTimeMillis()))
            );

            return ResponseEntity.ok(Map.of("message", "FCM silent test notification sent successfully"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to send FCM silent notification: " + e.getMessage()));
        }
    }


}
