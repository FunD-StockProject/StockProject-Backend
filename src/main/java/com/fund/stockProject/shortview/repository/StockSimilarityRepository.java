package com.fund.stockProject.shortview.repository;

import com.fund.stockProject.shortview.entity.StockSimilarity;
import com.fund.stockProject.shortview.entity.StockSimilarityId;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface StockSimilarityRepository extends JpaRepository<StockSimilarity, StockSimilarityId> {
    void deleteAllByType(String type);
    List<StockSimilarity> findByStockId1InAndType(List<Integer> stockIds, String type, Pageable pageable);
}