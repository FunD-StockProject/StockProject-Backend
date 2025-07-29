package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.ExperimentItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRepository extends JpaRepository<ExperimentItem, Integer> {
    @Query("SELECT e FROM ExperimentItem e WHERE e.email = :email")
    List<ExperimentItem> findExperimentItemsByEmail(@Param("email") String email);

    @Query("SELECT COUNT(e) FROM ExperimentItem e WHERE e.tradeStatus = 'PROGRESS'")
    int countByTradeStatusProgress();

    @Query("SELECT e FROM ExperimentItem e WHERE e.stock.id = :stockId AND e.buyAy = :today")
    Optional<ExperimentItem> findExperimentItemByStockIdAndBuyAt(@Param("stockId") Integer stockId, @Param("today") LocalDate today);

    @Query("SELECT e FROM ExperimentItem e WHERE e.stock.id = :stockId AND e.buyAt BETWEEN :start AND :end")
    Optional<ExperimentItem> findExperimentItemByStockIdAndBuyAtBetween(@Param("stockId") Integer stockId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e FROM experiment_item E WHERE DATE(e.buy_at) = CURDATE() - INTERVAL 5 DAY;")
    List<ExperimentItem> findExperimentItemsAfter5BusinessDays();
}
