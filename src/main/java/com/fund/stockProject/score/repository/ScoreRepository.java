package com.fund.stockProject.score.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = "stock")
    @Query("""
    SELECT s1
    FROM Score s1
    LEFT JOIN Score s2
      ON s1.stockId = s2.stockId AND s2.date = :today
    WHERE s1.date = :yesterday
      AND s2.stockId IS NULL
""")
    List<Score> findScoresWithoutTodayData(@Param("yesterday") LocalDate yesterday, @Param("today") LocalDate today);

    // ===== 성능 개선: 오늘 기준 상/하위 9건 바로 조회 (국가별 구분은 sentinel 값 사용) =====

    // 한국: scoreOversea = 9999 인 레코드가 한국 데이터
    @EntityGraph(attributePaths = "stock")
    List<Score> findTop9ByDateAndScoreOverseaEqualsOrderByDiffDesc(LocalDate date, Integer sentinel);

    @EntityGraph(attributePaths = "stock")
    List<Score> findTop9ByDateAndScoreOverseaEqualsOrderByDiffAsc(LocalDate date, Integer sentinel);

    // 해외: scoreKorea = 9999 인 레코드가 해외 데이터
    @EntityGraph(attributePaths = "stock")
    List<Score> findTop9ByDateAndScoreKoreaEqualsOrderByDiffDesc(LocalDate date, Integer sentinel);

    @EntityGraph(attributePaths = "stock")
    List<Score> findTop9ByDateAndScoreKoreaEqualsOrderByDiffAsc(LocalDate date, Integer sentinel);
}
