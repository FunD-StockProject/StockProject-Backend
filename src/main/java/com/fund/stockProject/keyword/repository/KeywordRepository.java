package com.fund.stockProject.keyword.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.stock.entity.Stock;

@Repository
public interface KeywordRepository extends JpaRepository<StockKeyword, Long> {

    @Query("SELECT sk.stock FROM StockKeyword sk WHERE sk.keyword.name = :keywordName ORDER BY sk.keyword.frequency DESC")
    List<Stock> findStocksByKeywordName(@Param("keywordName") String keywordName);
}
