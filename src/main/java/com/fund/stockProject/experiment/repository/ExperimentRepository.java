package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.ExperimentItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRepository extends JpaRepository<ExperimentItem, Integer> {
    @Query("SELECT e FROM ExperimentItem e WHERE e.userId = :userId")
    List<ExperimentItem> findExperimentItemsByUserId(@Param("userId") Integer userId);

    @Query("SELECT COUNT(e) FROM ExperimentItem e WHERE e.tradeStatus = 'PROGRESS'")
    int countByTradeStatusProgress();

    @Query("SELECT e FROM ExperimentItem e WHERE e.stock.id = :stockId AND e.buyAy = :today")
    Optional<ExperimentItem> findExperimentItemByStockIdAndBuyAt(@Param("stockId") Integer stockId, @Param("today") LocalDate today);
}
