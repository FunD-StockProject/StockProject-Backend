package com.fund.stockProject.preference.entity;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.stock.entity.Stock;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@IdClass(PreferenceId.class)
public class Preference extends Core {
    @Id
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false)
    private PreferenceType preferenceType;

    protected Preference() {}

    public Preference(User user, Stock stock, PreferenceType preferenceType) {
        this.user = user;
        this.stock = stock;
        this.preferenceType = preferenceType;
    }

    public void setPreferenceType(PreferenceType preferenceType) {
        this.preferenceType = preferenceType;
    }
}

