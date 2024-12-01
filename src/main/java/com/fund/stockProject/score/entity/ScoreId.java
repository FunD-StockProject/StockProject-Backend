package com.fund.stockProject.score.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ScoreId implements Serializable {

    private Integer stockId;  // Stock의 ID
    private LocalDate date;   // 날짜

    public ScoreId() {}

    public ScoreId(Integer stockId, LocalDate date) {
        this.stockId = stockId;
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreId scoreId = (ScoreId) o;
        return Objects.equals(stockId, scoreId.stockId) && Objects.equals(date, scoreId.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stockId, date);
    }
}