package com.fund.stockProject.stock.repository;

import com.fund.stockProject.stock.entity.QStock;
import com.fund.stockProject.stock.entity.Stock;

import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.ArrayList;
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
            .limit(5)
            .fetch();
    }

//    public List<Stock> findRelevantStocksByExchangeNumAndScore(Integer id) {
//        final Stock currentStock = jpaQueryFactory.selectFrom(QStock.stock).where(QStock.stock.id.eq(id)).fetchOne();
//
//        if (currentStock == null) {
//            System.out.println("Stock " + id + " is not found");
//
//            return new ArrayList<>();
//        }
//
//        if (currentStock.getExchangeNum().equals("1") || currentStock.getExchangeNum().equals("2")) {
//            return jpaQueryFactory.selectFrom(QStock.stock).where(
//                QStock.stock.exchangeNum.eq(currentStock.getExchangeNum())
//                    .and(QStock.stock.score.scoreKorea.between(
//                        currentStock.getScores().get(0).getScoreKorea() - 10,
//                        currentStock.getScores().get(0).getScoreKorea() + 10))
//                    .and(QStock.stock.ne(currentStock))
//            ).limit(3).fetch();
//        }
//
//        return jpaQueryFactory.selectFrom(QStock.stock)
//            .where(
//                QStock.stock.exchangeNum.eq(currentStock.getExchangeNum())
//                    .and(QStock.stock.score.scoreOversea.between(
//                        currentStock.getScores().get(0).getScoreOversea() - 10,
//                        currentStock.getScores().get(0).getScoreOversea() + 10))
//                    .and(QStock.stock.ne(currentStock))
//            ).limit(3).fetch();
//    }
}
