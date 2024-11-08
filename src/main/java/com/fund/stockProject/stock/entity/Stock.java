package com.fund.stockProject.stock.entity;

import com.fund.stockProject.global.entity.Core;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
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

    @OneToOne(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private Score score;

    @Column(nullable = false)
    private String symbol;

//    @Column(nullable = false)
//    영문주식명 찾기를 위해 nullbable 주석처리
    @Column
    private String symbolName;

    @Column(nullable = false)
    private String securityName;

    @Column(nullable = false)
    private String exchangeNum;

}
