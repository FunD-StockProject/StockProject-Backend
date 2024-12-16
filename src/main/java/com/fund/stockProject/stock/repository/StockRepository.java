package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.entity.Stock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Integer> {
    @Query("SELECT s FROM Stock s WHERE s.symbol = :symbolName OR s.symbolName = :symbolName")
    Optional<Stock> findFirstBySymbolOrSymbolName(@Param("symbolName") String symbolName);

    @Query("SELECT s FROM Stock s WHERE s.symbolName = :symbolName AND " +
        "((:country = 'KOREA' AND s.exchangeNum IN :koreaExchanges) OR " +
        "(:country = 'OVERSEA' AND s.exchangeNum IN :overseaExchanges))")
    Optional<Stock> findBySymbolNameAndCountryWithEnums(
        @Param("symbolName") String symbolName,
        @Param("country") String country,
        @Param("koreaExchanges") List<EXCHANGENUM> koreaExchanges,
        @Param("overseaExchanges") List<EXCHANGENUM> overseaExchanges
    );

    @Query("SELECT s FROM Stock s JOIN FETCH s.scores WHERE s.symbolName = :symbolName")
    Optional<Stock> findStockBySymbolNameWithScores(@Param("symbolName") String symbolName);

    @Query("SELECT s FROM Stock s JOIN FETCH s.scores WHERE s.symbol = :symbol")
    Optional<Stock> findStockBySymbolWithScores(@Param("symbol") String symbol);

    List<Stock> findStockBySymbolNameIsNull();
    Optional<Stock> findStockById(final Integer id);
}
