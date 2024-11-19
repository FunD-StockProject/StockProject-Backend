package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.entity.QStock;
import com.fund.stockProject.stock.entity.Stock;

import com.querydsl.core.types.dsl.BooleanExpression;
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
        final String[] keywordParts = keyword.split("");

        BooleanExpression condition = null;

        for (String part : keywordParts) {
            BooleanExpression containsPart = QStock.stock.symbolName.contains(part);
            condition = (condition == null) ? containsPart : condition.and(containsPart);
        }

        return jpaQueryFactory.selectFrom(QStock.stock)
            .where(condition)
            .limit(10)
            .fetch();
    }
}
