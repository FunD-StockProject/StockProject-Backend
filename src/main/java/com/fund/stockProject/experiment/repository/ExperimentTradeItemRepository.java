package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.ExperimentTradeItem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExperimentTradeItemRepository extends JpaRepository<ExperimentTradeItem, Integer> {
    @Query("SELECT e FROM ExperimentTradeItem e WHERE e.experiment.id = :experimentId ORDER BY e.tradeAt ASC")
    List<ExperimentTradeItem> findExperimentTradeItemsByExperimentId(@Param("experimentId") Integer experimentId);

    @Query("SELECT e FROM ExperimentTradeItem e WHERE e.experiment.id = :experimentId AND e.tradeAt BETWEEN :start and :end ORDER BY e.tradeAt")
    List<ExperimentTradeItem> findExperimentTradeItemsForToday(@Param("experimentId") Integer experimentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    // 사용자의 모든 실험 거래 항목 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM ExperimentTradeItem e WHERE e.experiment.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
