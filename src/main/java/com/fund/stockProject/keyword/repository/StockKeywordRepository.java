package com.fund.stockProject.keyword.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.stock.entity.Stock;

@Repository
public interface StockKeywordRepository extends JpaRepository<StockKeyword, Integer> {

    /**
     * 특정 Stock에 연관된 StockKeyword를 모두 조회
     *
     * @param stock 주식 엔티티
     * @return StockKeyword 리스트
     */
    List<StockKeyword> findByStock(Stock stock);

    /**
     * 특정 Stock에 연관된 StockKeyword를 모두 삭제
     *
     * @param stock 주식 엔티티
     */
    @Modifying
    @Query("DELETE FROM StockKeyword sk WHERE sk.stock = :stock")
    void deleteByStock(@Param("stock") Stock stock);
}