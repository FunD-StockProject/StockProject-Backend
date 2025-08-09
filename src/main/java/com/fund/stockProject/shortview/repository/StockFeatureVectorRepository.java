package com.fund.stockProject.shortview.repository;

import com.fund.stockProject.shortview.entity.StockFeatureVector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockFeatureVectorRepository extends JpaRepository<StockFeatureVector, Integer> {
    List<StockFeatureVector> findByStockIdIn(Collection<Integer> stockIds);
}