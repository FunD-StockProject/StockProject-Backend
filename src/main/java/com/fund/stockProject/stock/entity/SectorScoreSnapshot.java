package com.fund.stockProject.stock.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.stock.domain.COUNTRY;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "sector_score_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_sector_score_snapshot",
        columnNames = {"snapshot_date", "country", "sector"}
    )
)
public class SectorScoreSnapshot extends Core {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private COUNTRY country;

    @Column(nullable = false, length = 50)
    private String sector;

    @Column(nullable = false, length = 100)
    private String sectorName;

    @Column(nullable = false)
    private Integer averageScore;

    @Column(nullable = false)
    private Integer count;

    public SectorScoreSnapshot(LocalDate date, COUNTRY country, String sector, String sectorName,
                               Integer averageScore, Integer count) {
        this.date = date;
        this.country = country;
        this.sector = sector;
        this.sectorName = sectorName;
        this.averageScore = averageScore;
        this.count = count;
    }

    public void update(Integer averageScore, Integer count, String sectorName) {
        this.averageScore = averageScore;
        this.count = count;
        this.sectorName = sectorName;
    }
}
