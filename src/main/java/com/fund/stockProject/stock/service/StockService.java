package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.score.service.ScoreService;
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
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ScoreRepository scoreRepository;
    private final StockQueryRepository stockQueryRepository;
    private final SecurityService securityService;
    private final ScoreService scoreService;
    private final SecurityHttpConfig securityHttpConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;


    public StockSearchResponse searchStockBySymbolName(final String symbolName) {
        final Stock stock = stockRepository.findStockBySymbolName(symbolName)
            .orElseThrow(NoSuchElementException::new);

        return StockSearchResponse.builder()
            .stockId(stock.getId())
            .symbol(stock.getSymbol())
            .symbolName(stock.getSymbolName())
            .securityName(stock.getSecurityName())
            .exchangeNum(stock.getExchangeNum())
            .country(List.of("1", "2", "3").contains(stock.getExchangeNum()) ? COUNTRY.KOREA : COUNTRY.OVERSEA)
            .build();
    }


    public List<StockSearchResponse> autoCompleteKeyword(String keyword) {
        final List<Stock> stocks = stockQueryRepository.autocompleteKeyword(keyword);

        if (stocks.isEmpty()) {
            return null;
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
     * API 호출을 통해 symbol_name을 가져옵니다.
     *
     * @param symbol      종목 심볼
     * @param exchangeNum 거래소 코드 (String 타입)
     * @return API에서 가져온 symbol_name (prdt_name 값)
     */
    public String fetchSymbolName(String symbol, String exchangeNum) {
        try {
            HttpHeaders headers = securityHttpConfig.createSecurityHeaders();
            headers.set("tr_id", "CTPF1702R");

            String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/overseas-price/v1/quotations/search-info")
                    .queryParam("PDNO", symbol)
                    .queryParam("PRDT_TYPE_CD", exchangeNum)
                    .build())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            // JSON 파싱
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.path("output");

            return outputNode.path("prdt_name").asText(null);
        } catch (Exception e) {
            System.err.println("Error while fetching symbol name for symbol: " + symbol);
            e.printStackTrace();

            return null;
        }
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
                  .map(volumeRankResponses -> volumeRankResponses.stream()
                                                                 .map(rankResponse -> stockRepository.findStockBySymbolNameWithScores(rankResponse.getHtsKorIsnm()).orElse(null))
                                                                 .filter(Objects::nonNull) // null인 경우 건너뜀
                                                                 .map(stock -> {
                                                                     // 첫 인간지표인 경우, 점수 계산 실행
                                                                     LocalDate initialDate = LocalDate.of(1111, 11, 11);
                                                                     if (stock.getScores().get(0).getDate().isEqual(initialDate)) {
                                                                         int newScore = scoreService.getScoreById(stock.getId(), country).getScore();
                                                                         // 첫 Score의 점수를 갱신
                                                                         stock.getScores().get(0).setScoreKorea(newScore);
                                                                     }

                                                                     return StockSimpleResponse.builder()
                                                                                               .stockId(stock.getId())
                                                                                               .symbolName(stock.getSymbolName())
                                                                                               .score(stock.getScores().get(0).getScoreKorea()) // 최신 데이터를 보장
                                                                                               .build();
                                                                 })
                                                                 .collect(Collectors.toList()));
        } else if (country == COUNTRY.OVERSEA) {
            return securityService.getVolumeRankOversea()
                  .map(volumeRankResponses -> volumeRankResponses.stream()
                                                                 .map(rankResponse -> stockRepository.findStockBySymbolWithScores(rankResponse.getSymb()).orElse(null))
                                                                 .filter(Objects::nonNull) // null인 경우 건너뜀
                                                                 .map(stock -> {
                                                                     // 첫 인간지표인 경우, 점수 계산 실행
                                                                     LocalDate initialDate = LocalDate.of(1111, 11, 11);
                                                                     if (stock.getScores().get(0).getDate().isEqual(initialDate)) {
                                                                         int newScore = scoreService.getScoreById(stock.getId(), country).getScore();
                                                                         // 첫 Score의 점수를 갱신
                                                                         stock.getScores().get(0).setScoreOversea(newScore);
                                                                     }

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
        return convertToStockDiffResponses(topScores, country);
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
    private List<StockDiffResponse> convertToStockDiffResponses(List<Score> scores, COUNTRY country) {
        return scores.stream()
                     .map(score -> StockDiffResponse.builder()
                                                    .stockId(score.getStock().getId())
                                                    .symbolName(score.getStock().getSymbolName())
                                                    .score(country == COUNTRY.KOREA? score.getScoreKorea() : score.getScoreOversea())
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
        return convertToStockDiffResponses(bottomScores, country);
    }

    public List<StockSimpleResponse> getRelevantStocks(final Integer id) {
        final List<Stock> relevantStocksByExchangeNumAndScore = stockQueryRepository.findRelevantStocksByExchangeNumAndScore(id);

        if(relevantStocksByExchangeNumAndScore.isEmpty()){
            System.out.println("Stock " + id + " relevant Stocks are not found");

            return null;
        }

        return relevantStocksByExchangeNumAndScore.stream().map(
            stock -> StockSimpleResponse.builder()
                .stockId(stock.getId())
                .symbolName(stock.getSymbolName())
                .score(stock.getExchangeNum().equals("1") || stock.getExchangeNum().equals("2") ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea())
                .build()
        ).collect(Collectors.toList());
    }
}
