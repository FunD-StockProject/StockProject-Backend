package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.ExperimentTradeItem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentTradeItemRepository extends JpaRepository<ExperimentTradeItem, Integer> {
    @Query("SELECT e FROM ExperimentTradeItem e WHERE e.experiment.id = :experimentId")
    List<ExperimentTradeItem> findExperimentTradeItemsByExperimentId(@Param("experimentId") Integer experimentId);

    @Query("SELECT e FROM ExperimentTradeItem e WHERE e.experiment.id = :experimentId AND e.tradeAt BETWEEN :start and :end ORDER BY e.tradeAt")
    List<ExperimentTradeItem> findExperimentTradeItemsForToday(@Param("experimentId") Integer experimentId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
