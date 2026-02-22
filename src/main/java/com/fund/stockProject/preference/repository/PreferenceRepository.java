package com.fund.stockProject.preference.repository;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.entity.PreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceRepository extends JpaRepository<Preference, PreferenceId> {
    Optional<Preference> findByUserIdAndStockId(Integer userId, Integer stockId);
    List<Preference> findByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
    List<Preference> findByStockIdAndPreferenceType(Integer stockId, PreferenceType preferenceType);
    long countByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
    
    // 북마크 중 알림이 활성화된 개수
    long countByUserIdAndPreferenceTypeAndNotificationEnabled(
        Integer userId, PreferenceType preferenceType, Boolean notificationEnabled);

    boolean existsByUserIdAndStockIdAndPreferenceTypeAndNotificationEnabled(
        Integer userId, Integer stockId, PreferenceType preferenceType, Boolean notificationEnabled);

    // 사용자의 모든 Preference 조회
    List<Preference> findByUserId(Integer userId);

    // 사용자의 모든 Preference 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Preference p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);

    /**
     * 사용자의 특정 타입 Preference의 stockId만 조회 (성능 최적화: N+1 문제 해결)
     */
    @Query("SELECT p.stock.id FROM Preference p WHERE p.user.id = :userId AND p.preferenceType = :preferenceType")
    List<Integer> findStockIdsByUserIdAndPreferenceType(@Param("userId") Integer userId, @Param("preferenceType") PreferenceType preferenceType);
    
    /**
     * 특정 종목이 Preference에서 사용되었는지 확인 (배치 조회)
     */
    @Query("SELECT DISTINCT p.stock.id FROM Preference p WHERE p.stock.id IN :stockIds")
    List<Integer> findStockIdsUsedInPreferences(@Param("stockIds") List<Integer> stockIds);
}
