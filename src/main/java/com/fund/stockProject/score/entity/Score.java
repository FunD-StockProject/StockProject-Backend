package com.fund.stockProject.score.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ScoreId.class) // 복합 기본키 사용
public class Score extends Core {
    @Id
    private Integer stockId;

    @Id
    private LocalDate date; // 인간지표 날짜

    @OneToOne
    @MapsId("stockId") // stock_id를 매핑
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false)
    private Integer scoreKorea;

    @Column(nullable = false)
    private Integer scoreNaver;

    @Column(nullable = false)
    private Integer scoreReddit;

    @Column(nullable = false)
    private Integer scoreOversea;

    @Column(nullable = false)
    private Integer diff = 0; // 전일 대비

    @Builder
    public Score(
            Integer stockId,
            LocalDate date,
            Stock stock,
            Integer scoreKorea,
            Integer scoreNaver,
            Integer scoreReddit,
            Integer scoreOversea,
            Integer diff
    ) {
        this.stockId = stockId;
        this.date = date;
        this.stock = stock;
        this.scoreKorea = scoreKorea;
        this.scoreNaver = scoreNaver;
        this.scoreReddit = scoreReddit;
        this.scoreOversea = scoreOversea;
        this.diff = diff;
    }
}