package com.fund.stockProject.shortview.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주식 간 유사도를 저장하는 엔티티
 *
 * 🎯 목적:
 * - 주식 추천 시스템을 위한 유사도 데이터 저장
 * - 두 주식 간의 유사도를 점수로 표현
 * - 배치 작업으로 매일 새벽 4시에 자동 계산됨
 *
 * 📊 유사도 타입:
 * - "CF": 협업 필터링 기반 유사도 (사용자 행동 기반)
 * - "PATTERN": 점수 패턴 기반 유사도 (특징 벡터 기반)
 *
 * 🔑 복합키:
 * - stockId1: 기준 주식 ID
 * - stockId2: 비교 대상 주식 ID
 * - type: 유사도 타입
 *
 * 💡 사용 예시:
 * - stockId1=123, stockId2=456, type="CF", score=0.85
 * - "주식 123과 456의 협업 필터링 유사도는 0.85"
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_similarity")
@IdClass(StockSimilarityId.class)
public class StockSimilarity {
    @Id
    private Integer stockId1; // 기준 주식 ID

    @Id
    private Integer stockId2; // 비교 대상 주식 ID

    @Column(nullable = false)
    private String type; // 유사도 종류: "CF" (협업 필터링), "PATTERN" (점수 패턴)

    @Column(nullable = false)
    private double score; // 유사도 점수

    @Builder
    public StockSimilarity(Integer stockId1, Integer stockId2, String type, double score) {
        this.stockId1 = stockId1;
        this.stockId2 = stockId2;
        this.type = type;
        this.score = score;
    }
}