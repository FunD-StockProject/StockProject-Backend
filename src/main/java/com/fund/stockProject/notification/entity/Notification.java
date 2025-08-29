package com.fund.stockProject.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name="notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="user_id", nullable=false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="stock_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Stock stock; // null 가능 (시스템 알림 등)

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private NotificationType notificationType;

    private Integer oldScore;

    private Integer newScore;

    private Integer changeAbs;

    private String title;

    @Column(columnDefinition="text")
    private String body;

    @Builder.Default
    private Boolean isRead = false;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
