package com.fund.stockProject.score.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fund.stockProject.score.entity.Score;

@Repository
@EnableJpaRepositories
public interface ScoreRepository extends JpaRepository<Score, Integer> {
    @Query(value = """
SELECT DISTINCT s
FROM Score s
WHERE s.date = :date
  AND s.scoreOversea = 9999
ORDER BY s.diff DESC
LIMIT 9
""")
    List<Score> findTopScoresKorea(@Param("date") LocalDate date);

    @Query(value = """
SELECT DISTINCT s
FROM Score s
WHERE s.date = :date
  AND s.scoreKorea = 9999
ORDER BY s.diff DESC
LIMIT 9
""")
    List<Score> findTopScoresOversea(@Param("date") LocalDate date);

    @Query(value = """
SELECT DISTINCT s
FROM Score s
WHERE s.date = :date
  AND s.scoreOversea = 9999
ORDER BY s.diff
LIMIT 9
""")
    List<Score> findBottomScoresKorea(@Param("date") LocalDate date);

    @Query(value = """
SELECT DISTINCT s
FROM Score s
WHERE s.date = :date
  AND s.scoreKorea = 9999
ORDER BY s.diff
LIMIT 9
""")
    List<Score> findBottomScoresOversea(@Param("date") LocalDate date);

    /**
     * stock_id와 date로 특정 데이터가 존재하는지 확인
     */
    boolean existsByStockIdAndDate(Integer stockId, LocalDate date);

    /**
     * stock_id와 date로 Score 데이터 조회
     */
    Optional<Score> findByStockIdAndDate(Integer stockId, LocalDate date);

    @Transactional
    @Modifying // 수정 또는 삭제 쿼리에서 필요
    @Query("DELETE FROM Score s WHERE s.stockId = :stockId AND s.date = :date")
    void deleteByStockIdAndDate(@Param("stockId") Integer stockId, @Param("date") LocalDate date);

    @Query("""
    SELECT s1 
    FROM Score s1 
    LEFT JOIN Score s2 
      ON s1.stockId = s2.stockId AND s2.date = :today
    WHERE s1.date = :yesterday
      AND s2.stockId IS NULL
""")
    List<Score> findScoresWithoutTodayData(@Param("yesterday") LocalDate yesterday, @Param("today") LocalDate today);
}
