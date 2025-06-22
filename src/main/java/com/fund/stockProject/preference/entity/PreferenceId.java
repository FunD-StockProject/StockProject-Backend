package com.fund.stockProject.preference.entity;

import java.io.Serializable;
import java.util.Objects;

public class PreferenceId implements Serializable {
    private Integer user;
    private Integer stock;

    public PreferenceId() {}

    public PreferenceId(Integer user, Integer stockId) {
        this.user = user;
        this.stock = stockId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreferenceId that = (PreferenceId) o;
        return Objects.equals(user, that.user) &&
                Objects.equals(stock, that.stock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, stock);
    }
}
