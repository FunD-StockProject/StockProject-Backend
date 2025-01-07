package com.fund.stockProject.stock.repository;

import static com.fund.stockProject.score.entity.QScore.score;
import static com.fund.stockProject.stock.entity.QStock.stock;

import com.fund.stockProject.stock.domain.EXCHANGENUM;
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
        final BooleanExpression condition = stock.symbolName.like("%"+keyword+"%");
        return jpaQueryFactory.selectFrom(stock)
            .where(condition)
            .limit(30)
            .fetch();
    }

    public List<Stock> findRelevantStocksByExchangeNumAndScore(Stock stockById) {
        EXCHANGENUM currentExchangeNum = stockById.getExchangeNum(); // 현재 Stock의 ExchangeNum Enum

        if (currentExchangeNum == EXCHANGENUM.KOSPI || currentExchangeNum == EXCHANGENUM.KOSDAQ
            || currentExchangeNum == EXCHANGENUM.KOREAN_ETF) {
            return jpaQueryFactory.selectDistinct(stock).from(stock)
                .join(stock.scores, score).on()
                .where(
                    stock.exchangeNum.eq(currentExchangeNum) // Enum 비교
                        .and(score.scoreKorea.between(
                            stockById.getScores().get(0).getScoreKorea() - 10,
                            stockById.getScores().get(0).getScoreKorea() + 10))
                        .and(stock.id.ne(stockById.getId()))
                ).limit(3).fetch();
        }

        return jpaQueryFactory.selectDistinct(stock).from(stock)
            .join(stock.scores, score).on()
            .where(
                stock.exchangeNum.eq(currentExchangeNum) // Enum 비교
                    .and(score.scoreOversea.between(
                        stockById.getScores().get(0).getScoreOversea() - 10,
                        stockById.getScores().get(0).getScoreOversea() + 10))
                    .and(stock.id.ne(stockById.getId()))
            ).limit(3).fetch();
    }
}
