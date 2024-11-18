package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface StockRepository extends JpaRepository<Stock, Integer> {
    Optional<Stock> findStockBySymbolName(final String symbolName);
    Optional<Stock> findStockBySymbol(final String symbol);


    @Query("SELECT s FROM Stock s WHERE s.exchangeNum IN :exchangeNums AND s.symbolName IS NULL")
    Stream<Stock> streamByExchangeNumInAndSymbolNameIsNull(@Param("exchangeNums") List<String> exchangeNums);
}
