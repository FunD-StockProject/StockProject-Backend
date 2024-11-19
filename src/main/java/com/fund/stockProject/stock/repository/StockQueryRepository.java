package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.entity.QStock;
import com.fund.stockProject.stock.entity.Stock;

import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public class StockQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;

    public StockQueryRepository(JPAQueryFactory jpaQueryFactory) {
        this.jpaQueryFactory = jpaQueryFactory;
    }

    public List<Stock> autocompleteKeyword(String keyword) {
        return jpaQueryFactory.selectFrom(QStock.stock)
            .where(QStock.stock.symbolName.startsWith(keyword))
            .limit(10)
            .fetch();
    }
}
