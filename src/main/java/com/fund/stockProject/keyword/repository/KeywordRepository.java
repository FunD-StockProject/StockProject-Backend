package com.fund.stockProject.keyword.repository;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.stock.entity.Stock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Integer> {

    @Query("SELECT sk.stock FROM StockKeyword sk WHERE sk.keyword.name = :keywordName ORDER BY sk.keyword.frequency DESC")
    List<Stock> findStocksByKeywordName(@Param("keywordName") String keywordName);

    @Query("SELECT k FROM Keyword k WHERE k.lastUsedAt < :cutoffDate")
    List<Keyword> findByLastUsedAtBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT k FROM Keyword k order by frequency DESC limit 9")
    List<Keyword> findPopularKeyword();

    @Query("SELECT k FROM StockKeyword sk " +
        "JOIN sk.stock s " +
        "JOIN sk.keyword k " +
        "WHERE sk.stock.id = :stockId")
    List<Keyword> findKeywordsByStockId(@Param("stockId") Integer stockId);

}