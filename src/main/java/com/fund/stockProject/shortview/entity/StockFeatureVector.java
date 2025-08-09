package com.fund.stockProject.shortview.entity;

import com.fund.stockProject.stock.domain.SECTOR;
import com.fund.stockProject.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주식의 특징 벡터를 저장하는 엔티티
 *
 * 🎯 목적:
 * - 주식 추천 시스템을 위한 특징 데이터 저장
 * - 주식 간 유사도 계산을 위한 벡터 정보
 * - 배치 작업으로 매일 새벽 4시에 자동 계산됨
 *
 * 📊 특징 데이터:
 * - avgKoreaScore: 국내 점수 평균
 * - avgOverseaScore: 해외 점수 평균
 * - trendKorea: 국내 점수 추세 (기울기)
 * - trendOversea: 해외 점수 추세 (기울기)
 * - sector: 주식의 섹터 정보
 * - 🆕 volatilityKorea: 국내 점수 변동성 (표준편차)
 * - 🆕 volatilityOversea: 해외 점수 변동성 (표준편차)
 * - 🆕 momentumKorea: 국내 점수 모멘텀 (최근 변화율)
 * - 🆕 momentumOversea: 해외 점수 모멘텀 (최근 변화율)
 * - 🆕 consistencyKorea: 국내 점수 일관성 (안정성)
 * - 🆕 consistencyOversea: 해외 점수 일관성 (안정성)
 *
 * 🔗 연관관계:
 * - Stock과 1:1 관계 (stockId를 기본키로 사용)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_feature_vector")
public class StockFeatureVector {
    @Id
    private Integer stockId; // Stock의 ID와 동일한 값을 사용

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // stockId를 기본 키이자 외래 키로 매핑
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "gics_sector")
    private SECTOR sector = SECTOR.UNKNOWN; // 섹터 정보 (기본값: 미정/기타)

    // 기본 특징들
    private double avgKoreaScore;
    private double avgOverseaScore;
    private double trendKorea; // 점수 추세 (기울기)
    private double trendOversea;

    private double volatilityKorea; // 국내 점수 변동성
    private double volatilityOversea; // 해외 점수 변동성
    private double momentumKorea; // 국내 점수 모멘텀
    private double momentumOversea; // 해외 점수 모멘텀
    private double consistencyKorea; // 국내 점수 일관성
    private double consistencyOversea; // 해외 점수 일관성

    @Builder
    public StockFeatureVector(Stock stock, double avgKoreaScore, double avgOverseaScore,
                              double trendKorea, double trendOversea, double volatilityKorea,
                              double volatilityOversea, double momentumKorea, double momentumOversea,
                              double consistencyKorea, double consistencyOversea, SECTOR sector) {
        this.stock = stock;
        this.stockId = stock.getId();
        this.avgKoreaScore = avgKoreaScore;
        this.avgOverseaScore = avgOverseaScore;
        this.trendKorea = trendKorea;
        this.trendOversea = trendOversea;
        this.volatilityKorea = volatilityKorea;
        this.volatilityOversea = volatilityOversea;
        this.momentumKorea = momentumKorea;
        this.momentumOversea = momentumOversea;
        this.consistencyKorea = consistencyKorea;
        this.consistencyOversea = consistencyOversea;
        this.sector = sector != null ? sector : SECTOR.UNKNOWN;
    }

    public void setSector(SECTOR sector) {
        this.sector = sector != null ? sector : SECTOR.UNKNOWN;
    }

    public void setSector(String sectorName) {
        this.sector = SECTOR.fromName(sectorName);
    }
}