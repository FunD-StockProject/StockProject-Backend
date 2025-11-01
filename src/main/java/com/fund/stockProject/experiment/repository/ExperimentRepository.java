package com.fund.stockProject.experiment.repository;

import com.fund.stockProject.experiment.entity.Experiment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Integer> {
    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email ORDER BY e.roi ASC")
    List<Experiment> findExperimentsByEmail(@Param("email") String email); // 이메일을 기준으로 해당 유저의 실험정보 조회

    @Query("SELECT COUNT(e) FROM Experiment e WHERE e.status = :status")
    int countExperimentsByStatus(@Param("status") String status); // 상태(진행/완료) 별 실험 개수

    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email and e.status = :status ORDER BY e.roi ASC")
    List<Experiment> findExperimentsByEmailAndStatus(@Param("email") String email, @Param("status") String status); // 이메일과 완료된 실험을 기준으로 해당 유저의 실험정보 조회

    @Query("SELECT e FROM Experiment e WHERE e.id = :experimentId")
    Optional<Experiment> findExperimentByExperimentId(@Param("experimentId") Integer experimentId); // 실험Id 값으로 실험 내용 조회

    @Query("SELECT e FROM Experiment e WHERE e.stock.id = :stockId AND e.buyAt BETWEEN :startOfDay and :endOfDay")
    Optional<Experiment> findExperimentByStockIdForToday(@Param("stockId") Integer stockId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay); // 금일 진행중인 실험 상세정보

    @Query("SELECT count(e) FROM Experiment e WHERE e.buyAt BETWEEN :startOfWeek and :endOfWeek")
    int countExperimentsForWeek(@Param("startOfWeek") LocalDateTime startOfWeek, @Param("endOfWeek") LocalDateTime endOfWeek);

    @Query("SELECT e FROM Experiment e JOIN e.user u  WHERE u.email = :email and e.status = :status ORDER BY e.roi ASC")
    List<Experiment> findAllExperimentsByEmailAndStatus(@Param("email") String email, @Param("status") String status); // 이메일과 완료된 실험을 기준으로 해당 유저의 실험정보 조회

    @Query("SELECT e FROM Experiment e WHERE e.buyAt BETWEEN :start AND :end")
    List<Experiment> findExperimentsAfterFiveDays(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e FROM Experiment e WHERE e.buyAt > :start AND e.status = :status")
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
        + " GROUP BY u.email "
        + " ) AS total "
        + "LEFT JOIN "
        + " ( "
        + " SELECT u.email, COUNT(e.id) AS cnt "
        + " FROM experiment e "
        + " JOIN users u ON e.user_id = u.id "
        + " WHERE e.roi > 0 "
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
        + "    GROUP BY u.email "
        + "   ) AS total "
        + " LEFT JOIN "
        + "  ( "
        + "    SELECT u.email, COUNT(e.id) AS cnt "
        + "    FROM experiment e "
        + "    JOIN users u ON e.user_id = u.id "
        + "    WHERE e.roi > 0 "
        + "    GROUP BY u.email "
        + "  ) AS profitable "
        + " ON total.email = profitable.email "
        + ") a "
        + "WHERE a.ratio BETWEEN :startRange AND :endRange", nativeQuery = true)
    int countSameGradeUser(@Param("startRange") int startRange, @Param("endRange") int endRange);

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
        + "    DATE(e.buy_at) AS buy_date, "
        + "    e.roi, "
        + "    e.score "
        + "FROM experiment e "
        + "JOIN users u ON e.user_id = u.id "
        + "WHERE u.email = :email "
        + "AND e.status = 'COMPLETE' "
        + "AND e.roi IS NOT NULL "
        + "ORDER BY e.buy_at", nativeQuery = true)
    List<Object[]> findExperimentGroupByBuyAtByUser(@Param("email") String email);
    
    @Query("SELECT count(e) FROM Experiment e JOIN e.user u WHERE u.email = :email AND e.buyAt BETWEEN :startOfWeek and :endOfWeek")
    int countExperimentsForWeekByUser(@Param("email") String email, @Param("startOfWeek") LocalDateTime startOfWeek, @Param("endOfWeek") LocalDateTime endOfWeek);
}