package com.fund.stockProject.stock.entity;

import com.fund.stockProject.global.entity.Core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Score extends Core {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer scoreKorea;  // 국내

    @Column(nullable = false)
    private Integer scoreNaver;

    @Column(nullable = false)
    private Integer scorePax;

    @Column(nullable = false)
    private Integer scoreOversea;

    @OneToOne
    @MapsId // Score의 기본 키를 Stock의 scoreId와 매핑
    @PrimaryKeyJoinColumn  // 기본 키로 연결된 일대일 관계 설정
    private Stock stock;

    @Builder
    public Score(
            Integer scoreKorea,
            Integer scoreNaver,
            Integer scorePax,
            Integer scoreOversea
    ) {
        this.scoreKorea = scoreKorea;
        this.scorePax = scorePax;
        this.scoreNaver = scoreNaver;
        this.scoreOversea = scoreOversea;
    }
}
