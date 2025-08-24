package com.fund.stockProject.preference.repository;

import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.entity.PreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PreferenceRepository extends JpaRepository<Preference, PreferenceId> {
    Optional<Preference> findByUserIdAndStockId(Integer userId, Integer stockId);
    List<Preference> findByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
    long countByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
    List<Preference> findAllByPreferenceType(PreferenceType preferenceType);
    List<Preference> findFirst5ByUserAndPreferenceTypeOrderByCreatedAtDesc(User user, PreferenceType type);

    /**
     * 사용자가 "다시 보지 않기"로 설정한 주식 ID 목록 조회
     *
     * 🎯 숏뷰 연동을 위한 핵심 메서드
     * - 사용자가 특정 preferenceType(북마크/다시보지않기)으로 설정한 주식들의 ID만 조회
     * - 숏뷰 추천 시 해당 주식들을 제외하기 위해 사용
     *
     * @param userId 사용자 ID
     * @param preferenceType 조회할 preference 타입 (BOOKMARK 또는 NEVER_SHOW)
     * @return 해당 타입으로 설정된 주식 ID들의 Set
     */
    @Query("SELECT p.stock.id FROM Preference p WHERE p.user.id = :userId AND p.preferenceType = :preferenceType")
    Set<Integer> findStockIdsByUserIdAndPreferenceType(@Param("userId") Integer userId, @Param("preferenceType") PreferenceType preferenceType);
}
