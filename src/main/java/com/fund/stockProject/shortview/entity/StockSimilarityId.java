package com.fund.stockProject.shortview.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 복합키를 위한 ID 클래스
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StockSimilarityId implements Serializable {
    private Integer stockId1;
    private Integer stockId2;
}
