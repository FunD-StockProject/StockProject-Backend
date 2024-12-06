package com.fund.stockProject.stock.entity;

import java.util.ArrayList;
import java.util.List;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.stock.domain.EXCHANGENUM;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends Core {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_seq")
    @SequenceGenerator(name = "stock_seq", sequenceName = "stock_sequence", allocationSize = 1)
    private Integer id;

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("date DESC") // date 기준 내림차순 정렬
    private List<Score> scores = new ArrayList<>();

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String symbolName;

    @Column(nullable = false)
    private String securityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Convert(converter = ExchangeNumConverter.class)
    private EXCHANGENUM exchangeNum;

    // symbolName을 안전하게 설정하는 비즈니스 메서드
    public void updateSymbolNameIfNull(String symbolName) {
        if (this.symbolName == null) {
            this.symbolName = symbolName;
        }
    }

    @Override
    public String toString() {
        return "Stock{" +
            "id=" + id +
            ", scores=" + scores +
            ", symbol='" + symbol + '\'' +
            ", symbolName='" + symbolName + '\'' +
            ", securityName='" + securityName + '\'' +
            ", exchangeNum=" + exchangeNum +
            '}';
    }
}
