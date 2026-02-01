package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.Experiment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Integer> {
    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email ORDER BY e.roi ASC")
    List<Experiment> findExperimentsByEmail(@Param("email") String email); // 이메일을 기준으로 해당 유저의 실험정보 조회

    @Query("SELECT COUNT(e) FROM Experiment e WHERE e.status = :status")
    int countExperimentsByStatus(@Param("status") String status); // 상태(진행/완료) 별 실험 개수

    @Query("SELECT COUNT(e) FROM Experiment e JOIN e.user u WHERE u.email = :email AND e.status = :status")
    int countExperimentsByEmailAndStatus(@Param("email") String email, @Param("status") String status); // 이메일과 상태별 실험 개수

    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email and e.status = :status ORDER BY e.roi ASC")
    List<Experiment> findExperimentsByEmailAndStatus(@Param("email") String email, @Param("status") String status); // 이메일과 완료된 실험을 기준으로 해당 유저의 실험정보 조회

    @Query("SELECT e FROM Experiment e WHERE e.id = :experimentId")
    Optional<Experiment> findExperimentByExperimentId(@Param("experimentId") Integer experimentId); // 실험Id 값으로 실험 내용 조회

    @Query("SELECT e FROM Experiment e WHERE e.stock.id = :stockId AND e.buyAt BETWEEN :startOfDay and :endOfDay")
    Optional<Experiment> findExperimentByStockIdForToday(@Param("stockId") Integer stockId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay); // 금일 진행중인 실험 상세정보

    @Query("SELECT e FROM Experiment e WHERE e.stock.id = :stockId AND e.user.id = :userId AND e.buyAt BETWEEN :startOfDay and :endOfDay")
    Optional<Experiment> findExperimentByStockIdForTodayAndUser(@Param("stockId") Integer stockId, @Param("userId") Integer userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT e FROM Experiment e WHERE e.stock.id = :stockId AND e.user.id = :userId AND e.status = 'PROGRESS'")
    Optional<Experiment> findProgressExperimentByUserAndStock(@Param("userId") Integer userId, @Param("stockId") Integer stockId);

    @Query("SELECT count(e) FROM Experiment e WHERE e.buyAt BETWEEN :startOfWeek and :endOfWeek")
    int countExperimentsForWeek(@Param("startOfWeek") LocalDateTime startOfWeek, @Param("endOfWeek") LocalDateTime endOfWeek);

    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email and e.status = :status ORDER BY e.roi ASC")
    List<Experiment> findAllExperimentsByEmailAndStatus(@Param("email") String email, @Param("status") String status); // 이메일과 완료된 실험을 기준으로 해당 유저의 실험정보 조회

    /**
     * 5영업일 이상 지난 진행중인 실험 조회 (자동 매도 대상)
     * buyAt이 지정된 날짜 이전이고 status가 PROGRESS인 실험만 조회
     * Stock을 JOIN FETCH로 함께 로드하여 LazyInitializationException 방지
     */
    @Query("SELECT e FROM Experiment e JOIN FETCH e.stock WHERE e.buyAt <= :endDate AND e.status = 'PROGRESS'")
    List<Experiment> findExperimentsAfterFiveDays(@Param("endDate") LocalDateTime endDate);

    @Query("SELECT e FROM Experiment e JOIN FETCH e.stock WHERE e.buyAt > :start AND e.status = :status")
    List<Experiment> findProgressExperiments(@Param("start") LocalDateTime start, @Param("status") String status);

    @Query("SELECT AVG(e.roi) FROM Experiment e WHERE e.score BETWEEN :start AND :end AND e.user.email = :email AND e.status = 'COMPLETE'")
    Double findUserAvgRoi(@Param("start") int start, @Param("end") int end, @Param("email") String email);

    @Query("SELECT AVG(e.roi) FROM Experiment e WHERE e.score BETWEEN :start AND :end")
    double findTotalAvgRoi(@Param("start") int start, @Param("end") int end);

    @Query(value = "SELECT ROUND(IFNULL(profitable.cnt, 0) / total.cnt * 100, 1) AS ratio "
        + "FROM ( "
        + " SELECT u.email, COUNT(e.id) AS cnt "
        + " FROM experiment e "
        + " JOIN users u ON e.user_id = u.id "
        + " WHERE e.status = 'COMPLETE' "
        + " GROUP BY u.email "
        + " ) AS total "
        + "LEFT JOIN "
        + " ( "
        + " SELECT u.email, COUNT(e.id) AS cnt "
        + " FROM experiment e "
        + " JOIN users u ON e.user_id = u.id "
        + " WHERE e.status = 'COMPLETE' AND e.roi > 0 "
        + " GROUP BY u.email "
        + " ) AS profitable "
        + "ON total.email = profitable.email "
        + "WHERE total.email = :email ", nativeQuery = true)
    double findSuccessExperimentRate(@Param("email") String email);

    @Query(value = "SELECT count(*) "
        + "FROM "
        + "( "
        + "  SELECT ROUND(IFNULL(profitable.cnt, 0) / total.cnt * 100, 1) AS ratio "
        + "  FROM "
        + "  ( SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' "
        + "    GROUP BY u.email "
        + "    HAVING COUNT(e.id) > 0 "
        + "   ) AS total "
        + " LEFT JOIN "
        + "  ( "
        + "    SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' AND e.roi > 0 "
        + "    GROUP BY u.email "
        + "  ) AS profitable "
        + " ON total.email = profitable.email "
        + "  WHERE total.cnt > 0 "
        + ") a "
        + "WHERE a.ratio BETWEEN :startRange AND :endRange", nativeQuery = true)
    int countSameGradeUser(@Param("startRange") int startRange, @Param("endRange") int endRange);

    @Query(value = "SELECT count(*) "
        + "FROM "
        + "( "
        + "  SELECT ROUND(IFNULL(profitable.cnt, 0) / total.cnt * 100, 1) AS ratio "
        + "  FROM "
        + "  ( SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' "
        + "    GROUP BY u.email "
        + "    HAVING COUNT(e.id) > 0 "
        + "   ) AS total "
        + " LEFT JOIN "
        + "  ( "
        + "    SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' AND e.roi > 0 "
        + "    GROUP BY u.email "
        + "  ) AS profitable "
        + " ON total.email = profitable.email "
        + "  WHERE total.cnt > 0 "
        + ") a "
        + "WHERE a.ratio >= :startRange AND a.ratio < :endRange", nativeQuery = true)
    int countUsersBySuccessRateRange(@Param("startRange") double startRange, @Param("endRange") double endRange);

    @Query(value = "SELECT count(*) "
        + "FROM "
        + "( "
        + "  SELECT ROUND(IFNULL(profitable.cnt, 0) / total.cnt * 100, 1) AS ratio "
        + "  FROM "
        + "  ( SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' "
        + "    GROUP BY u.email "
        + "    HAVING COUNT(e.id) > 0 "
        + "   ) AS total "
        + " LEFT JOIN "
        + "  ( "
        + "    SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.status = 'COMPLETE' AND e.roi > 0 "
        + "    GROUP BY u.email "
        + "  ) AS profitable "
        + " ON total.email = profitable.email "
        + "  WHERE total.cnt > 0 "
        + ") a "
        + "WHERE a.ratio >= :startRange", nativeQuery = true)
    int countUsersBySuccessRateAtLeast(@Param("startRange") double startRange);

    @Query("SELECT COUNT(DISTINCT e.user.id) FROM Experiment e WHERE e.status = 'COMPLETE'")
    long countUsersWithCompletedExperiments();

    @Query(value = "SELECT "
        + "    sub.buy_date, "
        + "    ROUND(AVG(sub.roi), 1) AS avg_roi, "
        + "    ROUND(AVG(sub.score), 0) AS avg_score "
        + "FROM ( "
        + "    SELECT "
        + "        DATE(e.buy_at) AS buy_date, "
        + "        e.roi, "
        + "        e.score "
        + "    FROM experiment e "
        + ") AS sub "
        + "GROUP BY sub.buy_date "
        + "ORDER BY sub.buy_date ", nativeQuery = true)
    List<Object[]> findExperimentGroupByBuyAt();

    @Query(value = "SELECT "
        + "    sub.buy_date, "
        + "    ROUND(AVG(sub.roi), 1) AS avg_roi, "
        + "    ROUND(AVG(sub.score), 0) AS avg_score "
        + "FROM ( "
        + "    SELECT "
        + "        DATE(e.buy_at) AS buy_date, "
        + "        e.roi, "
        + "        e.score "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE u.email = :email "
        + "    AND e.status = 'COMPLETE' "
        + "    AND e.roi IS NOT NULL "
        + ") AS sub "
        + "GROUP BY sub.buy_date "
        + "ORDER BY sub.buy_date ", nativeQuery = true)
    List<Object[]> findExperimentGroupByBuyAtByUser(@Param("email") String email);
    
    @Query("SELECT count(e) FROM Experiment e JOIN e.user u WHERE u.email = :email AND e.buyAt BETWEEN :startOfWeek and :endOfWeek")
    int countExperimentsForWeekByUser(@Param("email") String email, @Param("startOfWeek") LocalDateTime startOfWeek, @Param("endOfWeek") LocalDateTime endOfWeek);
    
    @Query("SELECT count(e) FROM Experiment e JOIN e.user u WHERE u.email = :email AND e.status = 'COMPLETE' AND e.sellAt BETWEEN :startOfWeek and :endOfWeek")
    int countCompletedExperimentsForWeekByUser(@Param("email") String email, @Param("startOfWeek") LocalDateTime startOfWeek, @Param("endOfWeek") LocalDateTime endOfWeek);
    
    // 사용자의 모든 실험 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Experiment e WHERE e.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
    
    // 특정 종목이 실험에서 사용되었는지 확인
    @Query("SELECT COUNT(e) > 0 FROM Experiment e WHERE e.stock.id = :stockId")
    boolean existsByStockId(@Param("stockId") Integer stockId);
    
    // 여러 종목이 실험에서 사용되었는지 확인 (배치 조회)
    @Query("SELECT DISTINCT e.stock.id FROM Experiment e WHERE e.stock.id IN :stockIds")
    List<Integer> findStockIdsUsedInExperiments(@Param("stockIds") List<Integer> stockIds);
}
