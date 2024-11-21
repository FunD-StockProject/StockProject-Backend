package com.fund.stockProject.stock.service;

import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockDiffResponse;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.dto.response.StockSimpleResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final ScoreRepository scoreRepository;
    private final StockQueryRepository stockQueryRepository;
    private final SecurityService securityService;

    public StockService(StockRepository stockRepository, ScoreRepository scoreRepository, StockQueryRepository stockQueryRepository,
                        SecurityService securityService) {
        this.stockRepository = stockRepository;
        this.scoreRepository = scoreRepository;
        this.stockQueryRepository = stockQueryRepository;
        this.securityService = securityService;
    }

    public StockSearchResponse searchStockBySymbolName(final String symbolName) {
        final Stock stock = stockRepository.findStockBySymbolName(symbolName)
            .orElseThrow(NoSuchElementException::new);

        return StockSearchResponse.builder()
            .stockId(stock.getId())
            .symbol(stock.getSymbol())
            .symbolName(stock.getSymbolName())
            .securityName(stock.getSecurityName())
            .exchangeNum(stock.getExchangeNum())
            .scoreId(stock.getScore().getStockId())
            .scoreKorea(stock.getScore().getScoreKorea())
            .scoreOversea(stock.getScore().getScoreOversea())
            .build();
    }


    public List<StockSearchResponse> autoCompleteKeyword(String keyword) {
        final List<Stock> stocks = stockQueryRepository.autocompleteKeyword(keyword);

        if(stocks.isEmpty()){
            throw new NoSuchElementException();
        }

        return stocks.stream()
            .map(stock -> StockSearchResponse.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .symbolName(stock.getSymbolName())
                .securityName(stock.getSecurityName())
                .exchangeNum(stock.getExchangeNum())
                .scoreId(stock.getScore().getStockId())
                 .scoreKorea(stock.getScore().getScoreKorea())
                 .scoreOversea(stock.getScore().getScoreOversea())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 국내/해외 HOT 지표 반환
     * 현재는 거래량으로 처리
     * @param country 국내/해외 분류
     * @return 점수 정보
     */
    public Mono<List<StockSimpleResponse>> getHotStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getVolumeRankKorea()
                  .map(volumeRankResponses -> volumeRankResponses.stream().map(rankResponse -> {
                         final Stock stock = stockRepository.findStockBySymbolName(rankResponse.getHtsKorIsnm())
                                                            .orElseThrow(NoSuchElementException::new);
                         return StockSimpleResponse.builder()
                                 .stockId(stock.getId())
                                 .symbolName(stock.getSymbolName())
                                 .score(stock.getScore().getScoreKorea())
                                 .build();
                     })
                     .collect(Collectors.toList()));
        } else if (country == COUNTRY.OVERSEA) {
            return securityService.getVolumeRankOversea()
                  .map(volumeRankResponses -> volumeRankResponses.stream().map(rankResponse -> {
                         // 추후 symbolName으로 변경예정
                         final Stock stock = stockRepository.findStockBySymbol(rankResponse.getSymb())
                                                            .orElseThrow(NoSuchElementException::new);
                         return StockSimpleResponse.builder()
                                                   .stockId(stock.getId())
//                                                   .symbolName(stock.getSymbolName())
                                                   // 추후 symbolName 채워지면 변경예정
                                                   .symbolName(rankResponse.getName())
                                                   .score(stock.getScore().getScoreOversea())
                                                   .build();
                     })
                     .collect(Collectors.toList()));
        }
        return Mono.error(new IllegalArgumentException("Invalid country: " + country));
    }

    /**
     * 국내/해외 떡상 지표 반환
     * @param country 국내/해외 분류
     * @return 종목 정보
     */
    public List<StockDiffResponse> getRisingStocks(COUNTRY country) {
        LocalDate today = LocalDate.now();
        List<Score> topScores;

        if (country == COUNTRY.KOREA) {
            // 국내 로직: exchangeNum이 '1', '2'인 데이터를 조회
            topScores = getTopScores(today, List.of("1", "2", "3"), true);
        } else if (country == COUNTRY.OVERSEA) {
            // 해외 로직: exchangeNum이 '1', '2'가 아닌 데이터를 조회
            topScores = getTopScores(today, List.of("1", "2", "3"), false);
        } else {
            throw new IllegalArgumentException("Invalid country: " + country);
        }

        // 공통 변환 로직
        return convertToStockDiffResponses(topScores);
    }

    /**
     * 지정된 조건에 따라 상위 3개의 Score 데이터를 조회합니다.
     *
     * @param date         날짜
     * @param exchangeNums 거래소 코드 목록
     * @param isInClause   true면 IN, false면 NOT IN 조건
     * @return 상위 3개의 Score 데이터
     */
    private List<Score> getTopScores(LocalDate date, List<String> exchangeNums, boolean isInClause) {
        if (isInClause) {
            return scoreRepository.findTop3ByDateAndExchangeNums(date, exchangeNums);
        } else {
            return scoreRepository.findTop3ByDateAndExchangeNumsNotIn(date, exchangeNums);
        }
    }

    /**
     * 지정된 조건에 따라 하위 3개의 Score 데이터를 조회합니다.
     *
     * @param date         날짜
     * @param exchangeNums 거래소 코드 목록
     * @param isInClause   true면 IN, false면 NOT IN 조건
     * @return 하위 3개의 Score 데이터
     */
    private List<Score> getBottomScores(LocalDate date, List<String> exchangeNums, boolean isInClause) {
        if (isInClause) {
            return scoreRepository.findBottom3ByDateAndExchangeNums(date, exchangeNums);
        } else {
            return scoreRepository.findBottom3ByDateAndExchangeNumsNotIn(date, exchangeNums);
        }
    }

    /**
     * Score 데이터를 StockDiffResponse로 변환합니다.
     *
     * @param scores Score 데이터 목록
     * @return 변환된 StockDiffResponse 목록
     */
    private List<StockDiffResponse> convertToStockDiffResponses(List<Score> scores) {
        return scores.stream()
                     .map(score -> StockDiffResponse.builder()
                                                    .stockId(score.getStock().getId())
                                                    .symbolName(score.getStock().getSymbolName())
                                                    .score(score.getScoreKorea())
                                                    .diff(score.getDiff())
                                                    .build())
                     .collect(Collectors.toList());
    }

    /**
     * 국내/해외 떡락 지표 반환
     * @param country 국내/해외 분류
     * @return 종목 정보
     */
    // todo: 조회시점에 오늘 데이터가 없는 경우?
    public List<StockDiffResponse> getDescentStocks(COUNTRY country) {
        LocalDate today = LocalDate.now();
        List<Score> bottomScores;

        if (country == COUNTRY.KOREA) {
            // 국내 로직: exchangeNum이 '1', '2'인 데이터를 diff 오름차순으로 조회
            bottomScores = getBottomScores(today, List.of("1", "2", "3"), true);
        } else if (country == COUNTRY.OVERSEA) {
            // 해외 로직: exchangeNum이 '1', '2'가 아닌 데이터를 diff 오름차순으로 조회
            bottomScores = getBottomScores(today, List.of("1", "2", "3"), false);
        } else {
            throw new IllegalArgumentException("Invalid country: " + country);
        }

        // 공통 변환 로직
        return convertToStockDiffResponses(bottomScores);
    }
}
