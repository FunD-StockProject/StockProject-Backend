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

    // 사용자의 모든 Preference 조회
    List<Preference> findByUserId(Integer userId);

    // 사용자의 모든 Preference 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM Preference p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Integer userId);
}
