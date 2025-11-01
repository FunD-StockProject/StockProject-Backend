package com.fund.stockProject.shortview.dto;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "개인화 숏뷰 추천 종목 응답 DTO. 실시간 시세 조회 실패 시 가격 관련 필드는 null 일 수 있습니다.")
public class ShortViewResponse {

    @Schema(description = "종목 ID", example = "101")
    private final Integer stockId;
    @Schema(description = "종목 대표 이미지 URL", example = "https://cdn.example.com/stocks/AAPL.png")
    private final String imageUrl;
    @Schema(description = "종목 심볼/이름", example = "AAPL")
    private final String stockName;

    @Schema(description = "현재 가격 (실시간 조회 실패 시 null)", example = "187.12", nullable = true)
    private final Double price;
    @Schema(description = "가격 절대 변동값 (실시간 조회 실패 시 null)", example = "-1.45", nullable = true)
    private final Double priceDiff;
    @Schema(description = "가격 변동 퍼센트 (실시간 조회 실패 시 null)", example = "-0.77", nullable = true)
    private final Double priceDiffPerCent;

    @Schema(description = "현재 인간지표 점수", example = "82")
    private final Integer score;
    @Schema(description = "점수 변동값 (이전 대비)", example = "+5")
    private final Integer diff;

    @Schema(description = "추천 관련 키워드 최대 3개", example = "[\"AI\", \"반도체\", \"전기차\"]")
    private final List<String> keywords;
    
    @Schema(description = "종목 국가 정보 (KOREA 또는 OVERSEA)", example = "KOREA")
    private final COUNTRY country;

    @Builder
    private ShortViewResponse(Integer stockId, String imageUrl, String stockName,
                              Double price, Double priceDiff, Double priceDiffPerCent,
                              Integer score, Integer diff, List<String> keywords, COUNTRY country) {
        this.stockId = stockId;
        this.imageUrl = imageUrl;
        this.stockName = stockName;
        this.price = price;
        this.priceDiff = priceDiff;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.diff = diff;
        this.keywords = keywords;
        this.country = country;
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

        COUNTRY country = getCountryFromExchangeNum(stock.getExchangeNum());
        
        return ShortViewResponse.builder()
                .stockId(stock.getId())
                .imageUrl(stock.getImageUrl())
                .stockName(stock.getSymbolName())
                .price(stockInfo.getPrice())
                .priceDiff(stockInfo.getPriceDiff())
                .priceDiffPerCent(stockInfo.getPriceDiffPerCent())
                .score(score)
                .diff(scoreDiff)
                .keywords(keywordList)
                .country(country)
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

        COUNTRY country = getCountryFromExchangeNum(stock.getExchangeNum());
        
        return ShortViewResponse.builder()
                .stockId(stock.getId())
                .imageUrl(stock.getImageUrl())
                .stockName(stock.getSymbolName())
                .price(null)
                .priceDiff(null)
                .priceDiffPerCent(null)
                .score(score)
                .diff(scoreDiff)
                .keywords(keywordList)
                .country(country)
                .build();
    }

    private static boolean isKoreaStock(Stock stock) {
        if (stock.getExchangeNum() == null) return true;
        String exch = stock.getExchangeNum().name();
        return exch.contains("KOSPI") || exch.contains("KOSDAQ") || exch.contains("코스피") || exch.contains("코스닥");
    }
    
    private static COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangeNum) {
        if (exchangeNum == null) {
            return COUNTRY.KOREA; // 기본값
        }
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
            .contains(exchangeNum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }
}
