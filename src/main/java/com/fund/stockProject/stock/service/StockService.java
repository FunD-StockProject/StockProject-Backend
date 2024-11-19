package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.dto.response.StockSimpleResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StockService {

    private final SecurityService securityService;
    private final StockRepository stockRepository;
    private final StockQueryRepository stockQueryRepository;
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
            .scoreKorea(stock.getScore().getScoreKorea())
            .scoreNaver(stock.getScore().getScoreNaver())
            .scorePax(stock.getScore().getScorePax())
            .scoreOversea(stock.getScore().getScoreOversea())
            .build();
    }

    public List<StockSearchResponse> autoCompleteKeyword(String keyword) {
        final List<Stock> stocks = stockQueryRepository.autocompleteKeyword(keyword);

        if (stocks.isEmpty()) {
            throw new NoSuchElementException();
        }

        return stocks.stream()
            .map(stock -> StockSearchResponse.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .symbolName(stock.getSymbolName())
                .securityName(stock.getSecurityName())
                .exchangeNum(stock.getExchangeNum())
                .scoreKorea(stock.getScore().getScoreKorea())
                .scoreNaver(stock.getScore().getScoreNaver())
                .scorePax(stock.getScore().getScorePax())
                .scoreOversea(stock.getScore().getScoreOversea())
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
    public List<StockSimpleResponse> getRisingStocks(COUNTRY country) {
        List<StockSimpleResponse> stockSimpleResponses = new ArrayList<>();
        if (country == COUNTRY.KOREA) {
            for(int i = 1; i <= 9; i++) {
                final Stock stock = stockRepository.findById(i)
                                                   .orElseThrow(NoSuchElementException::new);
                 StockSimpleResponse stockSimpleResponse = StockSimpleResponse.builder()
                                   .stockId(stock.getId())
                                   .symbolName(stock.getSymbolName())
                                   .score(stock.getScore().getScoreKorea())
                                   .build();
                stockSimpleResponses.add(stockSimpleResponse);
            }
        } else if (country == COUNTRY.OVERSEA) {
            for(int i = 1; i <= 9; i++) {
                final Stock stock = stockRepository.findById(i)
                                                   .orElseThrow(NoSuchElementException::new);
                StockSimpleResponse stockSimpleResponse = StockSimpleResponse.builder()
                                                                             .stockId(stock.getId())
                                                                             .symbolName(stock.getSymbolName())
                                                                             .score(stock.getScore().getScoreKorea())
                                                                             .build();
                stockSimpleResponses.add(stockSimpleResponse);
            }
        }
        return stockSimpleResponses;
    }

    /**
     * 국내/해외 떡락 지표 반환
     * @param country 국내/해외 분류
     * @return 종목 정보
     */
    public List<StockSimpleResponse> getDescentStocks(COUNTRY country) {
        List<StockSimpleResponse> stockSimpleResponses = new ArrayList<>();
        if (country == COUNTRY.KOREA) {
            for(int i = 1; i <= 9; i++) {
                final Stock stock = stockRepository.findById(i)
                                                   .orElseThrow(NoSuchElementException::new);
                StockSimpleResponse stockSimpleResponse = StockSimpleResponse.builder()
                                                                             .stockId(stock.getId())
                                                                             .symbolName(stock.getSymbolName())
                                                                             .score(stock.getScore().getScoreKorea())
                                                                             .build();
                stockSimpleResponses.add(stockSimpleResponse);
            }
        } else if (country == COUNTRY.OVERSEA) {
            for(int i = 1; i <= 9; i++) {
                final Stock stock = stockRepository.findById(i)
                                                   .orElseThrow(NoSuchElementException::new);
                StockSimpleResponse stockSimpleResponse = StockSimpleResponse.builder()
                                                                             .stockId(stock.getId())
                                                                             .symbolName(stock.getSymbolName())
                                                                             .score(stock.getScore().getScoreKorea())
                                                                             .build();
                stockSimpleResponses.add(stockSimpleResponse);
            }
        }
        return stockSimpleResponses;
    }
}
