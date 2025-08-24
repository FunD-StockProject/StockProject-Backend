package com.fund.stockProject.shortview.dto;

import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 숏뷰 응답 DTO
 *
 * 📊 핵심 정보만 포함:
 * - 주식 이미지 URL
 * - 주식 이름
 * - 현재 가격
 * - 변동 가격
 * - 변동 퍼센트
 * - 인간지표 점수
 * - 인간지표 변동 점수
 * - 키워드 3개
 */
@Getter
public class ShortViewResponse {

    // 기본 정보
    private final String imageUrl;
    private final String stockName;

    // 실시간 가격 정보
    private final Double price;
    private final Double priceDiff;
    private final Double priceDiffPerCent;

    // 인간지표 점수 정보
    private final Integer score;
    private final Integer diff;

    // 관련 키워드 정보
    private final List<String> keywords;

    @Builder
    private ShortViewResponse(String imageUrl, String stockName,
                              Double price, Double priceDiff, Double priceDiffPerCent,
                              Integer score, Integer diff, List<String> keywords) {
        this.imageUrl = imageUrl;
        this.stockName = stockName;
        this.price = price;
        this.priceDiff = priceDiff;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.diff = diff;
        this.keywords = keywords;
    }

    /**
     * Stock 엔티티와 실시간 가격 정보를 결합하여 DTO로 변환합니다.
     */
    public static ShortViewResponse fromEntityWithPrice(Stock stock, StockInfoResponse stockInfo) {
        var latestScoreOpt = stock.getScores().stream().findFirst();
        int score = latestScoreOpt.map(s -> {
            if (isKoreaStock(stock)) {
                return s.getScoreKorea();
            } else {
                return s.getScoreOversea();
            }
        }).orElse(0);
        int scoreDiff = latestScoreOpt.map(s -> s.getDiff()).orElse(0);

        List<String> keywordList = stock.getStockKeywords().stream()
                .map(stockKeyword -> stockKeyword.getKeyword().getName())
                .limit(3)
                .collect(Collectors.toList());

        return ShortViewResponse.builder()
                .imageUrl(stock.getImageUrl())
                .stockName(stock.getSymbolName())
                .price(stockInfo.getPrice())
                .priceDiff(stockInfo.getPriceDiff())
                .priceDiffPerCent(stockInfo.getPriceDiffPerCent())
                .score(score)
                .diff(scoreDiff)
                .keywords(keywordList)
                .build();
    }

    /**
     * Stock 엔티티만으로 기본 DTO를 생성합니다.
     */
    public static ShortViewResponse fromEntity(Stock stock) {
        var latestScoreOpt = stock.getScores().stream().findFirst();
        int score = latestScoreOpt.map(s -> {
            if (isKoreaStock(stock)) {
                return s.getScoreKorea();
            } else {
                return s.getScoreOversea();
            }
        }).orElse(0);
        int scoreDiff = latestScoreOpt.map(s -> s.getDiff()).orElse(0);

        List<String> keywordList = stock.getStockKeywords().stream()
                .map(stockKeyword -> stockKeyword.getKeyword().getName())
                .limit(3)
                .collect(Collectors.toList());

        return ShortViewResponse.builder()
                .imageUrl(stock.getImageUrl())
                .stockName(stock.getSymbolName())
                .price(null)
                .priceDiff(null)
                .priceDiffPerCent(null)
                .score(score)
                .diff(scoreDiff)
                .keywords(keywordList)
                .build();
    }

    private static boolean isKoreaStock(Stock stock) {
        if (stock.getExchangeNum() == null) return true;
        String exch = stock.getExchangeNum().name();
        return exch.contains("KOSPI") || exch.contains("KOSDAQ") || exch.contains("코스피") || exch.contains("코스닥");
    }
}
