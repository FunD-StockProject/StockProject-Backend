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
            // todo: symbol로 찾도록 수정 검토
            return securityService.getVolumeRankKorea()
                  .map(volumeRankResponses -> volumeRankResponses.stream().map(rankResponse -> {
                         final Stock stock = stockRepository.findStockBySymbolNameWithScores(rankResponse.getHtsKorIsnm())
                                                            .orElseThrow(NoSuchElementException::new);

                         return StockSimpleResponse.builder()
                                                   .stockId(stock.getId())
                                                   .symbolName(stock.getSymbolName())
                                                   .score(stock.getScores().get(0).getScoreKorea()) // 최신 데이터를 보장
                                                   .build();
                     })
                     .collect(Collectors.toList()));
        } else if (country == COUNTRY.OVERSEA) {
            return securityService.getVolumeRankOversea()
                  .map(volumeRankResponses -> volumeRankResponses.stream().map(rankResponse -> {
                         System.out.println("rankResponse = " + rankResponse.getSymb());
                         final Stock stock = stockRepository.findStockBySymbolWithScores(rankResponse.getSymb())
                                                            .orElseThrow(NoSuchElementException::new);

                         return StockSimpleResponse.builder()
                                                   .stockId(stock.getId())
                                                   .symbolName(stock.getSymbolName())
                                                   .score(stock.getScores().get(0).getScoreOversea())
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
        List<Score> topScores = getTopScores(country);
        return convertToStockDiffResponses(topScores);
    }

    /**
     * 지정된 조건에 따라 상위 9개의 Score 데이터를 조회합니다.
     *
     * @return 상위 3개의 Score 데이터
     */
    private List<Score> getTopScores(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Score> topScores;

        if (country == COUNTRY.KOREA) {
            topScores = scoreRepository.findTopScoresKorea(today);

            // 오늘 날짜 데이터가 없을 경우 어제 날짜로 재조회
            if (topScores.isEmpty()) {
                topScores = scoreRepository.findTopScoresKorea(yesterday);
            }
        } else {
            topScores = scoreRepository.findTopScoresOversea(today);

            // 오늘 날짜 데이터가 없을 경우 어제 날짜로 재조회
            if (topScores.isEmpty()) {
                topScores = scoreRepository.findTopScoresOversea(yesterday);
            }
        }

        return topScores;
    }

    /**
     * 지정된 조건에 따라 하위 9개의 Score 데이터를 조회합니다.
     *
     * @return 하위 3개의 Score 데이터
     */
    private List<Score> getBottomScores(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Score> bottomScores;

        if (country == COUNTRY.KOREA) {
            bottomScores = scoreRepository.findBottomScoresKorea(today);

            // 오늘 날짜 데이터가 없을 경우 어제 날짜로 재조회
            if (bottomScores.isEmpty()) {
                bottomScores = scoreRepository.findBottomScoresKorea(yesterday);
            }
        } else {
            bottomScores = scoreRepository.findBottomScoresOversea(today);

            // 오늘 날짜 데이터가 없을 경우 어제 날짜로 재조회
            if (bottomScores.isEmpty()) {
                bottomScores = scoreRepository.findBottomScoresOversea(yesterday);
            }
        }

        return bottomScores;
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
    public List<StockDiffResponse> getDescentStocks(COUNTRY country) {
        List<Score> bottomScores = getBottomScores(country);
        return convertToStockDiffResponses(bottomScores);
    }

//    public List<StockSimpleResponse> getRelevantStocks(final Integer id) {
//        final List<Stock> relevantStocksByExchangeNumAndScore = stockQueryRepository.findRelevantStocksByExchangeNumAndScore(id);
//
//        if(relevantStocksByExchangeNumAndScore.isEmpty()){
//            System.out.println("Stock " + id + " relevant Stocks are not found");
//
//            return null;
//        }
//
//        return relevantStocksByExchangeNumAndScore.stream().map(
//            stock -> StockSimpleResponse.builder()
//                .stockId(stock.getId())
//                .symbolName(stock.getSymbolName())
//                .score(stock.getExchangeNum().equals("1") || stock.getExchangeNum().equals("2") ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea())
//                .build()
//        ).collect(Collectors.toList());
//    }
}
