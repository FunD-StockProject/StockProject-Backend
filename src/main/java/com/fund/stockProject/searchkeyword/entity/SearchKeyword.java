package com.fund.stockProject.searchkeyword.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.stock.domain.COUNTRY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "search_keyword", indexes = {
    @Index(name = "idx_keyword_country", columnList = "keyword, country"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class SearchKeyword extends Core {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private COUNTRY country;

    public static SearchKeyword of(String keyword, COUNTRY country) {
        return SearchKeyword.builder()
                .keyword(keyword)
                .country(country)
                .build();
    }
}
