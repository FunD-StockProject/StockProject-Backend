package com.fund.stockProject.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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

    private String type;

    @Column(columnDefinition="json")
    private String payload; // JSON 문자열

    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private Integer retryCount = 0;

    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    private Instant scheduledAt; // 예약 발송 시간 (null이면 즉시 발송)

    @Builder.Default
    private Instant createdAt = Instant.now();
}
