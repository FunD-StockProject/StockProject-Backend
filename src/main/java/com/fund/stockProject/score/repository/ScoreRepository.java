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
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;

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

    @Query("""
    SELECT s1 
    FROM Score s1 
    LEFT JOIN Score s2 
      ON s1.stockId = s2.stockId AND s2.date = :today
    WHERE s1.date = :yesterday
      AND s2.stockId IS NULL
""")
    List<Score> findScoresWithoutTodayData(@Param("yesterday") LocalDate yesterday, @Param("today") LocalDate today);

    @Query("SELECT s FROM Score s JOIN FETCH s.stock WHERE s.date IN (:today, :yesterday) AND s.scoreKorea = 9999")
    List<Score> findScoresByDatesOversea(@Param("today") LocalDate today, @Param("yesterday") LocalDate yesterday);

    @Query("SELECT s FROM Score s JOIN FETCH s.stock WHERE s.date IN (:today, :yesterday) AND s.scoreOversea = 9999")
    List<Score> findScoresByDatesKorea(@Param("today") LocalDate today, @Param("yesterday") LocalDate yesterday);

    @Query("SELECT s FROM Score s WHERE s.date IN (:today) AND s.scoreOversea = 9999")
    List<Score> findScoresByTodayOversea(@Param("today") LocalDate today);

    /**
     * stock_id로 최신 날짜의 Score 데이터 단건 조회
     */
    Optional<Score> findTopByStockIdOrderByDateDesc(Integer stockId);

    /**
     * 각 stock별 최신 Score 데이터를 조회 (해외 종목용)
     * s.scoreKorea = 9999 조건으로 해외 종목만 필터링
     */
    @Query("""
        SELECT s 
        FROM Score s 
        JOIN FETCH s.stock
        WHERE s.scoreKorea = 9999 
        AND s.date = (SELECT MAX(s2.date) FROM Score s2 WHERE s2.stockId = s.stockId AND s2.scoreKorea = 9999)
        ORDER BY s.date DESC, s.diff DESC
    """)
    List<Score> findLatestScoresByCountryOversea();

    /**
     * 각 stock별 최신 Score 데이터를 조회 (국내 종목용)
     * s.scoreOversea = 9999 조건으로 국내 종목만 필터링
     */
    @Query("""
        SELECT s 
        FROM Score s 
        JOIN FETCH s.stock
        WHERE s.scoreOversea = 9999 
        AND s.date = (SELECT MAX(s2.date) FROM Score s2 WHERE s2.stockId = s.stockId AND s2.scoreOversea = 9999)
        ORDER BY s.date DESC, s.diff DESC
    """)
    List<Score> findLatestScoresByCountryKorea();

    /**
     * 여러 stockId에 대한 오늘 날짜 점수를 배치로 조회
     */
    @Query("SELECT s FROM Score s WHERE s.stockId IN :stockIds AND s.date = :today")
    List<Score> findTodayScoresByStockIds(@Param("stockIds") List<Integer> stockIds, @Param("today") LocalDate today);

    /**
     * 여러 stockId에 대한 최신 점수를 배치로 조회
     * 각 stockId별로 가장 최근 날짜의 점수만 조회
     */
    @Query("""
        SELECT s 
        FROM Score s 
        WHERE s.stockId IN :stockIds 
        AND s.date = (SELECT MAX(s2.date) FROM Score s2 WHERE s2.stockId = s.stockId)
    """)
    List<Score> findLatestScoresByStockIds(@Param("stockIds") List<Integer> stockIds);

    /**
     * 각 stock별 최신 유효 Score 데이터를 조회 (국내 종목용)
     * scoreOversea = 9999 이며 scoreKorea != 9999 인 데이터 중 최신
     */
    @Query("""
        SELECT s
        FROM Score s
        JOIN FETCH s.stock st
        WHERE st.valid = true
        AND s.scoreOversea = 9999
        AND s.scoreKorea <> 9999
        AND s.date = (
            SELECT MAX(s2.date)
            FROM Score s2
            WHERE s2.stockId = s.stockId
            AND s2.scoreOversea = 9999
            AND s2.scoreKorea <> 9999
        )
    """)
    List<Score> findLatestValidScoresByCountryKorea();

    /**
     * 각 stock별 최신 유효 Score 데이터를 조회 (해외 종목용)
     * scoreKorea = 9999 이며 scoreOversea != 9999 인 데이터 중 최신
     */
    @Query("""
        SELECT s
        FROM Score s
        JOIN FETCH s.stock st
        WHERE st.valid = true
        AND s.scoreKorea = 9999
        AND s.scoreOversea <> 9999
        AND s.date = (
            SELECT MAX(s2.date)
            FROM Score s2
            WHERE s2.stockId = s.stockId
            AND s2.scoreKorea = 9999
            AND s2.scoreOversea <> 9999
        )
    """)
    List<Score> findLatestValidScoresByCountryOversea();

    /**
     * 특정 국내 섹터의 최신 유효 Score 데이터를 조회
     */
    @Query("""
        SELECT s
        FROM Score s
        JOIN FETCH s.stock st
        WHERE st.valid = true
        AND st.domesticSector = :sector
        AND s.scoreOversea = 9999
        AND s.scoreKorea <> 9999
        AND s.date = (
            SELECT MAX(s2.date)
            FROM Score s2
            WHERE s2.stockId = s.stockId
            AND s2.scoreOversea = 9999
            AND s2.scoreKorea <> 9999
        )
    """)
    List<Score> findLatestValidScoresByDomesticSector(@Param("sector") DomesticSector sector);

    /**
     * 특정 해외 섹터의 최신 유효 Score 데이터를 조회
     */
    @Query("""
        SELECT s
        FROM Score s
        JOIN FETCH s.stock st
        WHERE st.valid = true
        AND st.overseasSector = :sector
        AND s.scoreKorea = 9999
        AND s.scoreOversea <> 9999
        AND s.date = (
            SELECT MAX(s2.date)
            FROM Score s2
            WHERE s2.stockId = s.stockId
            AND s2.scoreKorea = 9999
            AND s2.scoreOversea <> 9999
        )
    """)
    List<Score> findLatestValidScoresByOverseasSector(@Param("sector") OverseasSector sector);

    Optional<Score> findTopByStockIdAndScoreOverseaAndScoreKoreaNotOrderByDateDesc(Integer stockId, Integer scoreOversea, Integer scoreKorea);

    Optional<Score> findTopByStockIdAndScoreKoreaAndScoreOverseaNotOrderByDateDesc(Integer stockId, Integer scoreKorea, Integer scoreOversea);
}
