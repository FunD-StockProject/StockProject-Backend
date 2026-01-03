package com.fund.stockProject.score.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @Column(name = "stock_id", nullable = false) // 물리적 열 이름 명시
    private Integer stockId;

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @ManyToOne
    @JoinColumn(name = "stock_id", referencedColumnName = "id", insertable = false, updatable = false)
    @JsonIgnore
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
    private Integer diff = 0;

    @Builder
    public Score(
            Integer stockId,
            LocalDate date,
            Integer scoreKorea,
            Integer scoreNaver,
            Integer scoreReddit,
            Integer scoreOversea,
            Integer diff
    ) {
        this.stockId = stockId;
        this.date = date;
        this.scoreKorea = scoreKorea;
        this.scoreNaver = scoreNaver;
        this.scoreReddit = scoreReddit;
        this.scoreOversea = scoreOversea;
        this.diff = diff;
    }

    // setStock 메서드
    public void setStock(Stock stock) {
        this.stock = stock;
        if (stock != null) {
            this.stockId = stock.getId(); // stock의 id로 stockId 동기화
        }
    }

    public void setScoreKorea(Integer scoreKorea) {
        this.scoreKorea = scoreKorea;
    }

    public void setScoreOversea(Integer scoreOversea) {
        this.scoreOversea = scoreOversea;
    }
}
