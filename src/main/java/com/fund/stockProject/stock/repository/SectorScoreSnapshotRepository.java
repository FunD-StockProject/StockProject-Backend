package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.entity.SectorScoreSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorScoreSnapshotRepository extends JpaRepository<SectorScoreSnapshot, Long> {

    @Query("SELECT MAX(s.date) FROM SectorScoreSnapshot s WHERE s.country = :country")
    Optional<LocalDate> findLatestDateByCountry(@Param("country") COUNTRY country);

    List<SectorScoreSnapshot> findByCountryAndDate(COUNTRY country, LocalDate date);

    Optional<SectorScoreSnapshot> findByCountryAndDateAndSector(COUNTRY country, LocalDate date, String sector);
}
