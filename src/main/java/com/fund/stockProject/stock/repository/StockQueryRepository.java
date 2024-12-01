package com.fund.stockProject.stock.repository;

import static com.fund.stockProject.score.entity.QScore.score;

import com.fund.stockProject.stock.entity.QStock;
import com.fund.stockProject.stock.entity.Stock;
import com.querydsl.core.types.dsl.BooleanExpression;
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
        final String[] keywordParts = keyword.split("");

        BooleanExpression condition = null;

        for (String part : keywordParts) {
            BooleanExpression containsPart = QStock.stock.symbolName.contains(part);
            condition = (condition == null) ? containsPart : condition.and(containsPart);
        }

        return jpaQueryFactory.selectFrom(QStock.stock)
            .where(condition)
            .limit(30)
            .fetch();
    }

    public List<Stock> findRelevantStocksByExchangeNumAndScore(Integer id) {
        final Stock currentStock = jpaQueryFactory.selectFrom(QStock.stock)
            .where(QStock.stock.id.eq(id)).fetchOne();

        if (currentStock == null) {
            System.out.println("Stock " + id + " is not found");

            return new ArrayList<>();
        }

        if (currentStock.getExchangeNum().equals("1") || currentStock.getExchangeNum().equals("2")) {
            return jpaQueryFactory.selectFrom(QStock.stock)
                .join(QStock.stock.scores, score).on()
                .where(
                    QStock.stock.exchangeNum.eq(currentStock.getExchangeNum())
                        .and(score.scoreKorea.between(
                            currentStock.getScores().get(0).getScoreKorea() - 10,
                            currentStock.getScores().get(0).getScoreKorea() + 10))
                        .and(QStock.stock.ne(currentStock))
                ).limit(3).fetch();
        }

        return jpaQueryFactory.selectFrom(QStock.stock)
            .join(QStock.stock.scores, score)
            .where(
                QStock.stock.exchangeNum.eq(currentStock.getExchangeNum())
                    .and(QStock.stock.scores.get(0).scoreOversea.between(
                        currentStock.getScores().get(0).getScoreOversea() - 10,
                        currentStock.getScores().get(0).getScoreOversea() + 10))
                    .and(QStock.stock.ne(currentStock))
            ).limit(3).fetch();
    }
}
