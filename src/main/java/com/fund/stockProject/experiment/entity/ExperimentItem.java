package com.fund.stockProject.experiment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.stock.entity.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @JsonIgnore
    private LocalDateTime buyAt;

    @JsonIgnore
    private LocalDateTime sellAt;

    @Column(nullable = false)
    private Double buyPrice;

    @Column(nullable = false)
    private Double sellPrice;

    @Column(nullable = false)
    private Double roi;

    @Column(nullable = false)
    private String tradeStatus;

    public void updateAutoSellResult(Double sellPrice, String tradeStatus, LocalDateTime sellAt, Double roi) {
        this.sellPrice = sellPrice;
        this.tradeStatus = tradeStatus;
        this.sellAt = sellAt;
        this.roi = roi;
    }
}
