package com.fund.stockProject.preference.repository;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.entity.PreferenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreferenceRepository extends JpaRepository<Preference, PreferenceId> {
    Optional<Preference> findByUserIdAndStockId(Integer userId, Integer stockId);
    List<Preference> findByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
    List<Preference> findByStockIdAndPreferenceType(Integer stockId, PreferenceType preferenceType);
    long countByUserIdAndPreferenceType(Integer userId, PreferenceType preferenceType);
}
