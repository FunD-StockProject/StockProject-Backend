package com.fund.stockProject.keyword.repository;

import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Integer> {
    @Query("SELECT k FROM Keyword k WHERE k.lastUsedAt < :cutoffDate")
    List<Keyword> findByLastUsedAtBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT k " +
        "FROM StockKeyword sk " +
        "JOIN sk.stock s " +
        "JOIN sk.keyword k " +
        "WHERE s.exchangeNum IN :exchanges " +
        "ORDER BY k.frequency DESC")
    List<Keyword> findPopularKeyword(@Param("exchanges") List<EXCHANGENUM> exchanges, Pageable pageable);

    @Query("SELECT k FROM StockKeyword sk " +
        "JOIN sk.stock s " +
        "JOIN sk.keyword k " +
        "WHERE sk.stock.id = :stockId " +
        "ORDER BY k.frequency DESC")
    List<Keyword> findKeywordsByStockId(@Param("stockId") Integer stockId, Pageable pageable);
}
