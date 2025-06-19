package com.fund.stockProject.preference.entity;

import java.io.Serializable;
import java.util.Objects;

public class PreferenceId implements Serializable {
    private Integer userId;
    private Integer stockId;

    public PreferenceId() {}

    public PreferenceId(Integer userId, Integer stockId) {
        this.userId = userId;
        this.stockId = stockId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreferenceId that = (PreferenceId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(stockId, that.stockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, stockId);
    }
}
