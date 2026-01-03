package com.fund.stockProject.keyword.entity;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int frequency; // 언급 빈도수

    @Column(name = "last_used_at")
    private LocalDate lastUsedAt; // 키워드가 마지막으로 사용된 날짜

    @Builder
    public Keyword(String name, int frequency) {
        this.name = name;
        this.frequency = frequency;
        this.lastUsedAt = LocalDate.now(); // 기본값 설정
    }

    public void updateFrequency(int frequency) {
        this.frequency = frequency;
        this.lastUsedAt = LocalDate.now(); // 사용된 날짜 업데이트
    }
}
