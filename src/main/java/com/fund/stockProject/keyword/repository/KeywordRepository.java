package com.fund.stockProject.keyword.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.stock.entity.Stock;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Integer> {

    @Query("SELECT sk.stock FROM StockKeyword sk WHERE sk.keyword.name = :keywordName ORDER BY sk.keyword.frequency DESC")
    List<Stock> findStocksByKeywordName(@Param("keywordName") String keywordName);

    @Query("SELECT k FROM Keyword k WHERE k.lastUsedAt < :cutoffDate")
    List<Keyword> findByLastUsedAtBefore(@Param("cutoffDate") LocalDate cutoffDate);
}
