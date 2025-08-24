package com.fund.stockProject.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 알림 발송을 위한 Outbox Pattern 구현체
 * 
 * Outbox Pattern: 데이터베이스 트랜잭션과 함께 메시지 발송을 보장하는 패턴
 * - 알림 생성과 발송 작업을 원자적으로 처리
 * - 발송 실패 시 자동 재시도
 * - 예약 발송 지원
 * 
 * 참고: https://microservices.io/patterns/data/transactional-outbox.html
 */
@Entity
@Table(name="outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;

    /**
     * 이벤트 타입 (예: "ALERT_CREATED", "NOTIFICATION_SENT")
     */
    private String type;

    /**
     * 발송할 데이터 (JSON 형태)
     * 예: {"notificationId": 123, "userId": 456, "quietPush": false}
     */
    @Column(columnDefinition="json")
    private String payload;

    /**
     * 발송 상태
     * - PENDING: 대기 중
     * - READY_TO_SEND: 발송 준비 완료
     * - COMPLETED: 발송 완료
     * - RETRY: 재시도 대기
     * - FAILED: 발송 실패
     */
    @Builder.Default
    private String status = "PENDING";

    /**
     * 재시도 횟수
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 다음 재시도 시간
     */
    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    /**
     * 예약 발송 시간 (null이면 즉시 발송)
     */
    private Instant scheduledAt;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
