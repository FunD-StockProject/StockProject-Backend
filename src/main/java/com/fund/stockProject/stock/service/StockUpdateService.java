package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StockUpdateService {

    private final StockRepository stockRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SecurityHttpConfig securityHttpConfig;

    /**
     * `exchange_num`이 "512", "513", "529"이고 `symbol_name`이 null인 데이터를 처리합니다.
     */
    @Transactional
    public void updateSymbolNames() {
        try (Stream<Stock> stockStream = stockRepository.streamByExchangeNumInAndSymbolNameIsNull(List.of("512", "513", "529"))) {
            stockStream.forEach(stock -> {
                try {
                    // API 호출 및 symbol_name 업데이트
                    String symbolName = fetchSymbolName(stock.getSymbol(), stock.getExchangeNum());
                    System.out.println("====UPDATE====");
                    System.out.println("stock id = " + stock.getId());
                    System.out.println("stock getSecurityName = " + stock.getSecurityName());
                    System.out.println("symbolName = " + symbolName);

                    if(symbolName == null) return;

                    stock.setSymbolName(symbolName);
                    stockRepository.saveAndFlush(stock); // 즉시 데이터베이스에 반영

                    System.out.println("after symbol = " + stock.getSymbolName());

                    // 각 반복에서 5초 대기
                    Thread.sleep(5000);
                } catch (Exception e) {
                    System.err.println("Failed to update symbol_name for stock: " + stock.getSymbol());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("Error during symbol name update process");
            e.printStackTrace();
        }
    }

    /**
     * API 호출을 통해 symbol_name을 가져옵니다.
     *
     * @param symbol       종목 심볼
     * @param exchangeNum  거래소 코드 (String 타입)
     * @return API에서 가져온 symbol_name (prdt_name 값)
     */
    private String fetchSymbolName(String symbol, String exchangeNum) {
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
}