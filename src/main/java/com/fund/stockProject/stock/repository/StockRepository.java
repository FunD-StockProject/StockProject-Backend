package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findStockBySymbolName(final String symbolName);
}
