package com.fund.stockProject.score.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.score.entity.Score;

@Repository
@EnableJpaRepositories
public interface ScoreRepository extends JpaRepository<Score, Integer> {
    /**
     * 오늘 날짜의 diff 값이 가장 높은 상위 3개 데이터를 조회합니다.
     * @param date 오늘 날짜
     * @return 상위 3개 Score 리스트
     */
    @Query("""
    SELECT s 
    FROM Score s
    WHERE s.date = :date 
      AND s.stock.exchangeNum IN :exchangeNums
    ORDER BY s.diff DESC
    LIMIT 9
""")
    List<Score> findTop3ByDateAndExchangeNums(@Param("date") LocalDate date, @Param("exchangeNums") List<String> exchangeNums);

    @Query("""
    SELECT s 
    FROM Score s
    WHERE s.date = :date 
      AND s.stock.exchangeNum NOT IN :exchangeNums
    ORDER BY s.diff DESC
    LIMIT 9
""")
    List<Score> findTop3ByDateAndExchangeNumsNotIn(@Param("date") LocalDate date, @Param("exchangeNums") List<String> exchangeNums);

    @Query("""
    SELECT s 
    FROM Score s
    WHERE s.date = :date 
      AND s.stock.exchangeNum IN :exchangeNums
    ORDER BY s.diff ASC
    LIMIT 9
""")
    List<Score> findBottom3ByDateAndExchangeNums(@Param("date") LocalDate date, @Param("exchangeNums") List<String> exchangeNums);

    @Query("""
    SELECT s 
    FROM Score s
    WHERE s.date = :date 
      AND s.stock.exchangeNum NOT IN :exchangeNums
    ORDER BY s.diff ASC
    LIMIT 9
""")
    List<Score> findBottom3ByDateAndExchangeNumsNotIn(@Param("date") LocalDate date, @Param("exchangeNums") List<String> exchangeNums);

    /**
     * stock_id와 date로 특정 데이터가 존재하는지 확인
     */
    boolean existsByStockIdAndDate(Integer stockId, LocalDate date);

    /**
     * stock_id와 date로 Score 데이터 조회
     */
    Optional<Score> findByStockIdAndDate(Integer stockId, LocalDate date);

}
