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
    
    // 특정 타입의 알림 조회
    Page<Notification> findByUserIdAndNotificationTypeOrderByCreatedAtDesc(
            Integer userId, NotificationType notificationType, Pageable pageable);
    
    // 읽지 않은 알림 개수
    long countByUserIdAndIsReadFalse(Integer userId);
    
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
