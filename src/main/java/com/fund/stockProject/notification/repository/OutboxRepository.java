package com.fund.stockProject.notification.repository;

import com.fund.stockProject.notification.entity.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Integer> {
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.scheduledAt <= :now")
    List<OutboxEvent> findByStatusAndScheduledAtBefore(@Param("status") String status, @Param("now") Instant now);
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND (e.scheduledAt IS NULL OR e.scheduledAt <= :now)")
    Page<OutboxEvent> findReadyToProcess(@Param("status") String status, @Param("now") Instant now, Pageable pageable);
    
    @Query("SELECT e FROM OutboxEvent e WHERE e.status IN :statuses AND (e.scheduledAt IS NULL OR e.scheduledAt <= :now)")
    Page<OutboxEvent> findReadyToProcessInStatuses(@Param("statuses") List<String> statuses, @Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'PENDING'
        AND e.scheduledAt IS NULL
    """)
    Page<OutboxEvent> findReadyImmediateEvents(Pageable pageable);

    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'READY_TO_SEND'
        AND e.scheduledAt <= :now
    """)
    Page<OutboxEvent> findReadyScheduledEvents(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.nextAttemptAt <= :now")
    List<OutboxEvent> findRetryableEvents(@Param("status") String status, @Param("now") Instant now);

    // 메모리 누수 방지를 위한 정리 메소드들 추가
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.createdAt < :createdBefore")
    List<OutboxEvent> findByStatusAndCreatedAtBefore(@Param("status") String status, @Param("createdBefore") Instant createdBefore);
}
