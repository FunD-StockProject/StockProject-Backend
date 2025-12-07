package com.fund.stockProject.stock.entity;

import java.util.ArrayList;
import java.util.List;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.stock.domain.EXCHANGENUM;

import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends Core {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockKeyword> stockKeywords = new ArrayList<>();

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

    @Enumerated(EnumType.STRING)
    private DomesticSector domesticSector;

    @Enumerated(EnumType.STRING)
    private OverseasSector overseasSector;

    private String imageUrl;

    private Boolean valid;

    public void updateSymbolNameIfNull(String symbolName) {
        if (this.symbolName == null) {
            this.symbolName = symbolName;
        }
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public void setDomesticSector(DomesticSector domesticSector) {
        this.domesticSector = domesticSector;
        this.overseasSector = null; // 국내 섹터 설정 시 해외 섹터는 null
    }

    public void setOverseasSector(OverseasSector overseasSector) {
        this.overseasSector = overseasSector;
        this.domesticSector = null; // 해외 섹터 설정 시 국내 섹터는 null
    }

    /**
     * 거래소에 따라 적절한 섹터를 설정합니다.
     */
    public void setSectorByExchange(String sectorCode, EXCHANGENUM exchangeNum) {
        if (exchangeNum == EXCHANGENUM.KOSPI || exchangeNum == EXCHANGENUM.KOSDAQ) {
            this.domesticSector = DomesticSector.fromCode(sectorCode, exchangeNum);
            this.overseasSector = null;
        } else if (exchangeNum == EXCHANGENUM.NAS || exchangeNum == EXCHANGENUM.NYS || exchangeNum == EXCHANGENUM.AMS) {
            this.overseasSector = OverseasSector.fromCode(sectorCode);
            this.domesticSector = null;
        }
    }

    /**
     * 현재 섹터 정보를 문자열로 반환합니다 (로깅용).
     */
    public String getSectorString() {
        if (domesticSector != null && domesticSector != DomesticSector.UNKNOWN) {
            return domesticSector.getName();
        } else if (overseasSector != null && overseasSector != OverseasSector.UNKNOWN) {
            return overseasSector.getName();
        }
        return "Unknown";
    }

    // 종목 데이터 임포트를 위한 생성자
    public Stock(String symbol, String symbolName, String securityName, EXCHANGENUM exchangeNum, Boolean valid) {
        this.symbol = symbol;
        this.symbolName = symbolName;
        this.securityName = securityName;
        this.exchangeNum = exchangeNum;
        this.valid = valid;
    }
}
