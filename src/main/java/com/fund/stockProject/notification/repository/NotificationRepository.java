package com.fund.stockProject.notification.repository;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    
    // 사용자별 알림 목록 조회 (최신순)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    // 유효한 알림 목록 조회 (점수 변동 알림은 유효한 점수만)
    @Query("""
        SELECT n FROM Notification n
        WHERE n.user.id = :userId
        AND (
            n.notificationType <> :scoreSpike
            OR (
                COALESCE(n.changeAbs, 1) <> 0
                AND n.title IS NOT NULL
                AND n.body IS NOT NULL
            )
        )
        ORDER BY n.createdAt DESC
    """)
    Page<Notification> findValidByUserIdOrderByCreatedAtDesc(
            @Param("userId") Integer userId,
            @Param("scoreSpike") NotificationType scoreSpike,
            Pageable pageable);
    
    // 특정 타입의 알림 조회
    Page<Notification> findByUserIdAndNotificationTypeOrderByCreatedAtDesc(
            Integer userId, NotificationType notificationType, Pageable pageable);

    // 유효한 특정 타입의 알림 조회
    @Query("""
        SELECT n FROM Notification n
        WHERE n.user.id = :userId
        AND n.notificationType = :notificationType
        AND (
            :notificationType <> :scoreSpike
            OR (
                COALESCE(n.changeAbs, 1) <> 0
                AND n.title IS NOT NULL
                AND n.body IS NOT NULL
            )
        )
        ORDER BY n.createdAt DESC
    """)
    Page<Notification> findValidByUserIdAndNotificationTypeOrderByCreatedAtDesc(
            @Param("userId") Integer userId,
            @Param("notificationType") NotificationType notificationType,
            @Param("scoreSpike") NotificationType scoreSpike,
            Pageable pageable);
    
    // 읽지 않은 알림 개수
    long countByUserIdAndIsReadFalse(Integer userId);

    // 유효한 읽지 않은 알림 개수
    @Query("""
        SELECT COUNT(n) FROM Notification n
        WHERE n.user.id = :userId
        AND n.isRead = false
        AND (
            n.notificationType <> :scoreSpike
            OR (
                COALESCE(n.changeAbs, 1) <> 0
                AND n.title IS NOT NULL
                AND n.body IS NOT NULL
            )
        )
    """)
    long countValidByUserIdAndIsReadFalse(
            @Param("userId") Integer userId,
            @Param("scoreSpike") NotificationType scoreSpike);
    
    // 모든 알림 읽음 처리
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Integer userId);

    // 사용자의 모든 알림 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
