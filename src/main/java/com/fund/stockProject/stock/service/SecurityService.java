package com.fund.stockProject.stock.service;


import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockChartResponse.PriceInfo;
import com.fund.stockProject.stock.dto.response.StockKoreaMarketCapResponse;
import com.fund.stockProject.stock.dto.response.StockKoreaRisingDescentResponse;
import com.fund.stockProject.stock.dto.response.StockOverseaRankResponse;
import com.fund.stockProject.stock.entity.Stock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockKoreaVolumeRankResponse;
import com.fund.stockProject.stock.dto.response.StockOverseaVolumeRankResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);

    private final SecurityHttpConfig securityHttpConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * 국내, 해외 주식 정보 조회
     */
    public Mono<StockInfoResponse> getSecurityStockInfoKorea2(Integer id, String symbolName, String securityName, String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-price-2")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // 레퍼런스에 따라 대문자로 수정
                    .queryParam("FID_INPUT_ISCD", symbol)  // 레퍼런스에 따라 대문자로 수정
                    .build())
                .headers(httpHeaders -> {
                    HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                    headers.set("tr_id", "FHPST01010000"); // 레퍼런스에 따라 inquire-price-2는 FHPST01010000 사용
                    httpHeaders.addAll(headers);
                })
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> parseFStockInfoKorea2(response, id, symbolName, securityName, symbol, exchangenum, country));
        } else if (country == COUNTRY.OVERSEA) {
            return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/price")
                    .queryParam("AUTH", "")
                    .queryParam("EXCD", exchangenum.name())
                    .queryParam("SYMB", symbol)
                    .build())
                .headers(httpHeaders -> {
                    HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                    headers.set("tr_id", "HHDFS00000300"); // 추가 헤더 설정
                    httpHeaders.addAll(headers);
                })
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> parseFStockInfoOversea(response, id, symbolName, securityName, symbol, exchangenum, country));
        } else {
            return Mono.error(new UnsupportedOperationException("COUNTRY 입력 에러"));
        }
    }

    private Mono<StockInfoResponse> parseFStockInfoKorea2(String response, Integer id, String symbolName, String securityName, String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        try {
            log.debug("Parsing StockInfo response - symbol: {}, response: {}", symbol, response);
            JsonNode rootNode = objectMapper.readTree(response);
            
            // 에러 코드 확인
            String resultCode = rootNode.path("rt_cd").asText("");
            String messageCode = rootNode.path("msg_cd").asText("");
            String message = rootNode.path("msg1").asText("");
            
            // 응답이 에러인 경우 처리
            if (!resultCode.isEmpty() && !resultCode.equals("0")) {
                log.error("API returned error - symbol: {}, rt_cd: {}, msg_cd: {}, msg1: {}, full response: {}", 
                    symbol, resultCode, messageCode, message, response);
                return Mono.error(new UnsupportedOperationException("국내 종목 정보 조회 실패: " + message + " (rt_cd: " + resultCode + ")"));
            }
            
            JsonNode outputNode = rootNode.get("output");
            StockInfoResponse stockInfoResponse = new StockInfoResponse();
            
            // 기본 정보 설정 (output이 없어도)
            stockInfoResponse.setStockId(id);
            stockInfoResponse.setSymbolName(symbolName);
            stockInfoResponse.setSecurityName(securityName);
            stockInfoResponse.setSymbol(symbol);
            stockInfoResponse.setExchangeNum(exchangenum);
            stockInfoResponse.setCountry(country);

            if (outputNode != null && !outputNode.isNull() && !outputNode.isEmpty()) {
                // 안전하게 필드 파싱
                JsonNode yesterdayPriceNode = outputNode.get("stck_prdy_clpr");
                if (yesterdayPriceNode != null && !yesterdayPriceNode.isNull() && yesterdayPriceNode.canConvertToLong()) {
                    try {
                        stockInfoResponse.setYesterdayPrice(yesterdayPriceNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse yesterdayPrice for symbol: {}, value: {}", symbol, yesterdayPriceNode.asText());
                    }
                }
                
                JsonNode currentPriceNode = outputNode.get("stck_prpr");
                if (currentPriceNode != null && !currentPriceNode.isNull() && currentPriceNode.canConvertToLong()) {
                    try {
                        stockInfoResponse.setPrice(currentPriceNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse currentPrice for symbol: {}, value: {}", symbol, currentPriceNode.asText());
                    }
                }
                
                // 가격 정보가 하나도 없는 경우 에러
                if (stockInfoResponse.getPrice() == null && stockInfoResponse.getYesterdayPrice() == null) {
                    log.error("Both price and yesterdayPrice are missing - symbol: {}, output: {}", symbol, outputNode.toString());
                    return Mono.error(new UnsupportedOperationException("주가 정보가 없습니다 (symbol: " + symbol + ")"));
                }
            } else {
                log.error("output node is null, missing or empty - symbol: {}, full response: {}", symbol, response);
                return Mono.error(new UnsupportedOperationException("주가 정보가 없습니다 (output node missing, symbol: " + symbol + ")"));
            }

            log.debug("Successfully parsed StockInfo - symbol: {}, price: {}, yesterdayPrice: {}", 
                symbol, stockInfoResponse.getPrice(), stockInfoResponse.getYesterdayPrice());
            return Mono.just(stockInfoResponse);
        } catch (Exception e) {
            log.error("Failed to parse StockInfo response - symbol: {}, response: {}, error: {}", 
                symbol, response, e.getMessage(), e);
            return Mono.error(new UnsupportedOperationException("국내 종목 정보 파싱 실패: " + e.getMessage()));
        }
    }

    /**
     * 국내, 해외 주식 정보 조회
     */
    public Mono<StockInfoResponse> getSecurityStockInfoKorea(Integer id, String symbolName, String securityName, String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return webClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                                         .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // 레퍼런스에 따라 대문자 유지
                                                         .queryParam("FID_INPUT_ISCD", symbol)  // 레퍼런스에 따라 대문자 유지
                                                         .build())
                            .headers(httpHeaders -> {
                                HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                                headers.set("tr_id", "FHKST01010100"); // 레퍼런스에 따라 inquire-price는 FHKST01010100 사용
                                httpHeaders.addAll(headers);
                            })
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(response -> parseFStockInfoKorea(response, id, symbolName, securityName, symbol, exchangenum, country));
        } else if (country == COUNTRY.OVERSEA) {
            return webClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/price")
                                                         .queryParam("AUTH", "")
                                                         .queryParam("EXCD", exchangenum.name())
                                                         .queryParam("SYMB", symbol)
                                                         .build())
                            .headers(httpHeaders -> {
                                HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                                headers.set("tr_id", "HHDFS00000300"); // 추가 헤더 설정
                                httpHeaders.addAll(headers);
                            })
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(response -> parseFStockInfoOversea(response, id, symbolName, securityName, symbol, exchangenum, country));
        } else {
            return Mono.error(new UnsupportedOperationException("COUNTRY 입력 에러"));
        }
    }

    private Mono<StockInfoResponse> parseFStockInfoKorea(String response, Integer id, String symbolName, String securityName, String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        try {
            log.debug("Parsing StockInfo response (inquire-price) - symbol: {}, response: {}", symbol, response);
            JsonNode rootNode = objectMapper.readTree(response);
            
            // 에러 코드 확인
            String resultCode = rootNode.path("rt_cd").asText("");
            String messageCode = rootNode.path("msg_cd").asText("");
            String message = rootNode.path("msg1").asText("");
            
            // 응답이 에러인 경우 처리
            if (!resultCode.isEmpty() && !resultCode.equals("0")) {
                log.error("API returned error - symbol: {}, rt_cd: {}, msg_cd: {}, msg1: {}, full response: {}", 
                    symbol, resultCode, messageCode, message, response);
                return Mono.error(new UnsupportedOperationException("국내 종목 정보 조회 실패: " + message + " (rt_cd: " + resultCode + ")"));
            }
            
            JsonNode outputNode = rootNode.get("output");
            StockInfoResponse stockInfoResponse = new StockInfoResponse();
            
            // 기본 정보 설정
            stockInfoResponse.setStockId(id);
            stockInfoResponse.setSymbolName(symbolName);
            stockInfoResponse.setSecurityName(securityName);
            stockInfoResponse.setSymbol(symbol);
            stockInfoResponse.setExchangeNum(exchangenum);
            stockInfoResponse.setCountry(country);

            if (outputNode != null && !outputNode.isNull() && !outputNode.isEmpty()) {
                // 안전하게 필드 파싱
                JsonNode yesterdayPriceNode = outputNode.get("stck_prdy_clpr");
                if (yesterdayPriceNode != null && !yesterdayPriceNode.isNull()) {
                    try {
                        stockInfoResponse.setYesterdayPrice(yesterdayPriceNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse yesterdayPrice for symbol: {}, value: {}", symbol, yesterdayPriceNode.asText());
                    }
                }
                
                JsonNode currentPriceNode = outputNode.get("stck_prpr");
                if (currentPriceNode != null && !currentPriceNode.isNull()) {
                    try {
                        stockInfoResponse.setPrice(currentPriceNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse currentPrice for symbol: {}, value: {}", symbol, currentPriceNode.asText());
                    }
                }
                
                JsonNode priceDiffNode = outputNode.get("prdy_vrss");
                if (priceDiffNode != null && !priceDiffNode.isNull()) {
                    try {
                        stockInfoResponse.setPriceDiff(priceDiffNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse priceDiff for symbol: {}", symbol);
                    }
                }
                
                JsonNode priceDiffPercentNode = outputNode.get("prdy_ctrt");
                if (priceDiffPercentNode != null && !priceDiffPercentNode.isNull()) {
                    try {
                        stockInfoResponse.setPriceDiffPerCent(priceDiffPercentNode.asDouble());
                    } catch (Exception e) {
                        log.warn("Failed to parse priceDiffPerCent for symbol: {}", symbol);
                    }
                }
                
                // 가격 정보가 하나도 없는 경우 에러
                if (stockInfoResponse.getPrice() == null && stockInfoResponse.getYesterdayPrice() == null) {
                    log.error("Both price and yesterdayPrice are missing - symbol: {}, output: {}", symbol, outputNode.toString());
                    return Mono.error(new UnsupportedOperationException("주가 정보가 없습니다 (symbol: " + symbol + ")"));
                }
            } else {
                log.error("output node is null, missing or empty - symbol: {}, rt_cd: {}, msg_cd: {}, msg1: {}, full response: {}", 
                    symbol, resultCode, messageCode, message, response);
                return Mono.error(new UnsupportedOperationException("주가 정보가 없습니다 (output node missing, symbol: " + symbol + ")"));
            }

            log.debug("Successfully parsed StockInfo (inquire-price) - symbol: {}, price: {}, yesterdayPrice: {}", 
                symbol, stockInfoResponse.getPrice(), stockInfoResponse.getYesterdayPrice());
            return Mono.just(stockInfoResponse);
        } catch (Exception e) {
            log.error("Failed to parse StockInfo response - symbol: {}, response: {}, error: {}", 
                symbol, response, e.getMessage(), e);
            return Mono.error(new UnsupportedOperationException("국내 종목 정보 파싱 실패: " + e.getMessage()));
        }
    }

    private Mono<StockInfoResponse> parseFStockInfoOversea(String response, Integer id, String symbolName, String securityName, String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");
            StockInfoResponse stockInfoResponse = new StockInfoResponse();

            if (outputNode != null) {
                stockInfoResponse.setStockId(id);
                stockInfoResponse.setSymbolName(symbolName);
                stockInfoResponse.setSecurityName(securityName);
                stockInfoResponse.setCountry(country);
                stockInfoResponse.setSymbol(symbol);
                stockInfoResponse.setExchangeNum(exchangenum);
                stockInfoResponse.setYesterdayPrice(outputNode.get("base").asDouble()); // 전일종가
                stockInfoResponse.setPrice(outputNode.get("last").asDouble()); // 현재가
                // 해외는 diff가 절대값이므로 절대값에 따라 음수로 변경
                if(outputNode.get("rate").asDouble() < 0) {
                    stockInfoResponse.setPriceDiff(outputNode.get("diff").asDouble() * -1);
                }
                else{
                    stockInfoResponse.setPriceDiff(outputNode.get("diff").asDouble());
                }
                stockInfoResponse.setPriceDiffPerCent(outputNode.get("rate").asDouble());
            }

            return Mono.just(stockInfoResponse);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("해외 종목 정보가 없습니다"));
        }
    }

    /**
     * (종목차트) 한국 거래량 순위 조회
     */
    public Mono<List<StockKoreaVolumeRankResponse>> getVolumeRankKoreaForCategory() {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/volume-rank")
                                                     .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                                     .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                                                     .queryParam("FID_INPUT_ISCD", "0001")
                                                     .queryParam("FID_DIV_CLS_CODE", "0")
                                                     .queryParam("FID_BLNG_CLS_CODE", "0")
                                                     .queryParam("FID_TRGT_CLS_CODE", "111111111")
                                                     .queryParam("FID_TRGT_EXLS_CLS_CODE", "000000")
                                                     .queryParam("FID_INPUT_PRICE_1", "")
                                                     .queryParam("FID_INPUT_PRICE_2", "")
                                                     .queryParam("FID_VOL_CNT", "0")
                                                     .queryParam("FID_INPUT_DATE_1", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "FHPST01710000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseFVolumeRankKoreaForCategory);
    }

    /**
     * 한국 거래량 순위 조회
     */
    public Mono<List<StockKoreaVolumeRankResponse>> getVolumeRankKorea() {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/volume-rank")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                .queryParam("FID_INPUT_ISCD", "0001")
                .queryParam("FID_DIV_CLS_CODE", "0")
                .queryParam("FID_BLNG_CLS_CODE", "0")
                .queryParam("FID_TRGT_CLS_CODE", "111111111")
                .queryParam("FID_TRGT_EXLS_CLS_CODE", "000000")
                .queryParam("FID_INPUT_PRICE_1", "")
                .queryParam("FID_INPUT_PRICE_2", "")
                .queryParam("FID_VOL_CNT", "0")
                .queryParam("FID_INPUT_DATE_1", "")
                .build())
            .headers(httpHeaders -> {
                HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                headers.set("tr_id", "FHPST01710000"); // 추가 헤더 설정
                httpHeaders.addAll(headers);
            })
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(this::parseFVolumeRankKorea);
    }

    /**
     * 국내 시가총액 순위 조회
     */
    public Mono<List<StockKoreaMarketCapResponse>> getMarketCapRankKorea() {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/ranking/market-cap")
                                                     .queryParam("fid_cond_mrkt_div_code", "J")
                                                     .queryParam("fid_cond_scr_div_code", "20174")
                                                     .queryParam("fid_div_cls_code", "0")
                                                     .queryParam("fid_input_iscd", "0000")
                                                     .queryParam("fid_trgt_cls_code", "0")
                                                     .queryParam("fid_trgt_exls_cls_code", "0")
                                                     .queryParam("fid_input_price_1", "")
                                                     .queryParam("fid_input_price_2", "")
                                                     .queryParam("fid_vol_cnt", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "FHPST01740000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseFMarketCapKorea);
    }

    /**
     * 국내 상승/하락 순위 조회
     */
    public Mono<List<StockKoreaRisingDescentResponse>> getRisingDescentRankKorea(boolean isRising) {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/ranking/fluctuation")
                                                     .queryParam("fid_cond_mrkt_div_code", "J")
                                                     .queryParam("fid_cond_scr_div_code", "20170")
                                                     .queryParam("fid_input_iscd", "0000")
                                                     .queryParam("fid_rank_sort_cls_code", isRising ? "0" : "1")
                                                     .queryParam("fid_input_cnt_1", "0")
                                                     .queryParam("fid_prc_cls_code", "0")
                                                     .queryParam("fid_input_price_1", "")
                                                     .queryParam("fid_input_price_2", "")
                                                     .queryParam("fid_vol_cnt", "")
                                                     .queryParam("fid_trgt_cls_code", "0")
                                                     .queryParam("fid_trgt_exls_cls_code", "0")
                                                     .queryParam("fid_div_cls_code", "0")
                                                     .queryParam("fid_rsfl_rate1", "")
                                                     .queryParam("fid_rsfl_rate2", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "FHPST01700000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseFRisingDescentKorea);
    }

    /**
     * 해외 거래량 순위 조회
     */
    public Mono<List<StockOverseaVolumeRankResponse>> getVolumeRankOversea() {
        return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/inquire-search")
                .queryParam("AUTH", "")
                .queryParam("EXCD", "NAS")
                .queryParam("CO_YN_PRICECUR", "")
                .queryParam("CO_ST_PRICECUR", "")
                .queryParam("CO_EN_PRICECUR", "")
                .queryParam("CO_YN_RATE", "")
                .queryParam("CO_ST_RATE", "")
                .queryParam("CO_EN_RATE", "")
                .queryParam("CO_YN_VALX", "")
                .queryParam("CO_ST_VALX", "")
                .queryParam("CO_EN_VALX", "")
                .queryParam("CO_YN_SHAR", "")
                .queryParam("CO_ST_SHAR", "")
                .queryParam("CO_EN_SHAR", "")
                .queryParam("CO_YN_VOLUME", "")
                .queryParam("CO_ST_VOLUME", "")
                .queryParam("CO_EN_VOLUME", "")
                .queryParam("CO_YN_AMT", "")
                .queryParam("CO_ST_AMT", "")
                .queryParam("CO_EN_AMT", "")
                .queryParam("CO_YN_EPS", "")
                .queryParam("CO_ST_EPS", "")
                .queryParam("CO_EN_EPS", "")
                .queryParam("CO_YN_PER", "")
                .queryParam("CO_ST_PER", "")
                .queryParam("CO_EN_PER", "")
                .build())
            .headers(httpHeaders -> {
                HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                headers.set("tr_id", "HHDFS76410000"); // 추가 헤더 설정
                httpHeaders.addAll(headers);
            })
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(this::parseFVolumeRankOversea);
    }

    /**
     * (종목차트용) 해외 거래량 순위 조회
     */
    public Mono<List<StockOverseaRankResponse>> getVolumeRankOverseaForCategory() {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/inquire-search")
                                                     .queryParam("AUTH", "")
                                                     .queryParam("EXCD", "NAS")
                                                     .queryParam("CO_YN_PRICECUR", "")
                                                     .queryParam("CO_ST_PRICECUR", "")
                                                     .queryParam("CO_EN_PRICECUR", "")
                                                     .queryParam("CO_YN_RATE", "")
                                                     .queryParam("CO_ST_RATE", "")
                                                     .queryParam("CO_EN_RATE", "")
                                                     .queryParam("CO_YN_VALX", "")
                                                     .queryParam("CO_ST_VALX", "")
                                                     .queryParam("CO_EN_VALX", "")
                                                     .queryParam("CO_YN_SHAR", "")
                                                     .queryParam("CO_ST_SHAR", "")
                                                     .queryParam("CO_EN_SHAR", "")
                                                     .queryParam("CO_YN_VOLUME", "")
                                                     .queryParam("CO_ST_VOLUME", "")
                                                     .queryParam("CO_EN_VOLUME", "")
                                                     .queryParam("CO_YN_AMT", "")
                                                     .queryParam("CO_ST_AMT", "")
                                                     .queryParam("CO_EN_AMT", "")
                                                     .queryParam("CO_YN_EPS", "")
                                                     .queryParam("CO_ST_EPS", "")
                                                     .queryParam("CO_EN_EPS", "")
                                                     .queryParam("CO_YN_PER", "")
                                                     .queryParam("CO_ST_PER", "")
                                                     .queryParam("CO_EN_PER", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "HHDFS76410000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseFVolumeRankOverseaForCategory);
    }

    /**
     * 해외 급상승/하락 순위 조회
     */
    public Mono<List<StockOverseaRankResponse>> getRisingDescentRankOversea(boolean isRising) {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/inquire-search")
                                                     .queryParam("AUTH", "")
                                                     .queryParam("EXCD", "NAS")
                                                     .queryParam("CO_YN_PRICECUR", "")
                                                     .queryParam("CO_ST_PRICECUR", "")
                                                     .queryParam("CO_EN_PRICECUR", "")
                                                     .queryParam("CO_YN_RATE", "1")
                                                     .queryParam("CO_ST_RATE", isRising ? "0" : "-100")
                                                     .queryParam("CO_EN_RATE", isRising ? "5000" : "-15")
                                                     .queryParam("CO_YN_VALX", "")
                                                     .queryParam("CO_ST_VALX", "")
                                                     .queryParam("CO_EN_VALX", "")
                                                     .queryParam("CO_YN_SHAR", "")
                                                     .queryParam("CO_ST_SHAR", "")
                                                     .queryParam("CO_EN_SHAR", "")
                                                     .queryParam("CO_YN_VOLUME", "")
                                                     .queryParam("CO_ST_VOLUME", "")
                                                     .queryParam("CO_EN_VOLUME", "")
                                                     .queryParam("CO_YN_AMT", "")
                                                     .queryParam("CO_ST_AMT", "")
                                                     .queryParam("CO_EN_AMT", "")
                                                     .queryParam("CO_YN_EPS", "")
                                                     .queryParam("CO_ST_EPS", "")
                                                     .queryParam("CO_EN_EPS", "")
                                                     .queryParam("CO_YN_PER", "")
                                                     .queryParam("CO_ST_PER", "")
                                                     .queryParam("CO_EN_PER", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "HHDFS76410000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(response -> parseFRisingDescentOversea(response, isRising));
    }

    /**
     * 해외 시가총액 순위 조회
     */
    public Mono<List<StockOverseaRankResponse>> getMarketCapOversea() {
        return webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/inquire-search")
                                                     .queryParam("AUTH", "")
                                                     .queryParam("EXCD", "NAS")
                                                     .queryParam("CO_YN_PRICECUR", "")
                                                     .queryParam("CO_ST_PRICECUR", "")
                                                     .queryParam("CO_EN_PRICECUR", "")
                                                     .queryParam("CO_YN_RATE", "")
                                                     .queryParam("CO_ST_RATE", "")
                                                     .queryParam("CO_EN_RATE", "")
                                                     .queryParam("CO_YN_VALX", "1")
                                                     .queryParam("CO_ST_VALX", "100000000")
                                                     .queryParam("CO_EN_VALX", "100000000000")
                                                     .queryParam("CO_YN_SHAR", "")
                                                     .queryParam("CO_ST_SHAR", "")
                                                     .queryParam("CO_EN_SHAR", "")
                                                     .queryParam("CO_YN_VOLUME", "")
                                                     .queryParam("CO_ST_VOLUME", "")
                                                     .queryParam("CO_EN_VOLUME", "")
                                                     .queryParam("CO_YN_AMT", "")
                                                     .queryParam("CO_ST_AMT", "")
                                                     .queryParam("CO_EN_AMT", "")
                                                     .queryParam("CO_YN_EPS", "")
                                                     .queryParam("CO_ST_EPS", "")
                                                     .queryParam("CO_EN_EPS", "")
                                                     .queryParam("CO_YN_PER", "")
                                                     .queryParam("CO_ST_PER", "")
                                                     .queryParam("CO_EN_PER", "")
                                                     .build())
                        .headers(httpHeaders -> {
                            HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                            headers.set("tr_id", "HHDFS76410000"); // 추가 헤더 설정
                            httpHeaders.addAll(headers);
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap(this::parseFMarketCapOversea);
    }

    private Mono<List<StockOverseaRankResponse>> parseFRisingDescentOversea(String response, boolean isRising) {
        try {
            // 응답 데이터를 담을 리스트 초기화
            List<StockOverseaRankResponse> responseDataList = new ArrayList<>();

            // JSON 파싱
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2"); // "output2"에서 데이터 추출

            if (outputNode != null) {
                // outputNode를 ArrayList로 변환
                List<JsonNode> nodeList = new ArrayList<>();
                outputNode.forEach(nodeList::add);

                // isRising이 false이면 데이터 순서를 반대로 변경
                if (!isRising) {
                    Collections.reverse(nodeList);
                }

                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : nodeList) {
                    if (count >= 6) { // 최대 6개의 데이터만 처리
                        break;
                    }

                    // 각 데이터 노드를 StockOverseaRankResponse에 매핑
                    StockOverseaRankResponse responseData = new StockOverseaRankResponse();
                    responseData.setSymb(node.get("symb").asText());
                    responseData.setName(node.get("name").asText());
                    responseData.setLast(node.get("last").asText());
                    responseData.setSign(node.get("sign").asText());
                    responseData.setDiff(node.get("diff").asText());
                    responseData.setRate(node.get("rate").asText());
                    responseData.setTvol(node.get("tvol").asText());
                    responseData.setValx(node.get("valx").asText());
                    responseData.setRank(node.get("rank").asText());

                    responseDataList.add(responseData); // 리스트에 추가
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            // 예외 발생 시 Mono.error 반환
            return Mono.error(new UnsupportedOperationException("해외 상승/하락 정보를 처리하는 중 오류가 발생했습니다.", e));
        }
    }

    // 현재는 나스닥기준, 추후 변경 예정
    private Mono<List<StockOverseaRankResponse>> parseFMarketCapOversea(String response) {
        try {
            List<StockOverseaRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 6) {
                        break; // 6개까지만 추가
                    }

                    StockOverseaRankResponse responseData = new StockOverseaRankResponse();
                    responseData.setSymb(node.get("symb").asText());
                    responseData.setName(node.get("name").asText());
                    responseData.setLast(node.get("last").asText());
                    responseData.setSign(node.get("sign").asText());
                    responseData.setDiff(node.get("diff").asText());
                    responseData.setRate(node.get("rate").asText());
                    responseData.setTvol(node.get("tvol").asText());
                    responseData.setValx(node.get("valx").asText());
                    responseData.setRank(node.get("rank").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    // 현재는 나스닥기준, 추후 변경 예정
    private Mono<List<StockOverseaRankResponse>> parseFVolumeRankOverseaForCategory(String response) {
        try {
            List<StockOverseaRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 6) {
                        break; // 6개까지만 추가
                    }

                    StockOverseaRankResponse responseData = new StockOverseaRankResponse();
                    responseData.setSymb(node.get("symb").asText());
                    responseData.setName(node.get("name").asText());
                    responseData.setLast(node.get("last").asText());
                    responseData.setSign(node.get("sign").asText());
                    responseData.setDiff(node.get("diff").asText());
                    responseData.setRate(node.get("rate").asText());
                    responseData.setTvol(node.get("tvol").asText());
                    responseData.setValx(node.get("valx").asText());
                    responseData.setRank(node.get("rank").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    // 현재는 나스닥기준, 추후 변경 예정
    private Mono<List<StockOverseaVolumeRankResponse>> parseFVolumeRankOversea(String response) {
        try {
            List<StockOverseaVolumeRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 9) {
                        break; // 9개까지만 추가
                    }

                    StockOverseaVolumeRankResponse responseData = new StockOverseaVolumeRankResponse();
                    responseData.setSymb(node.get("symb").asText());
                    responseData.setName(node.get("name").asText());
                    responseData.setTvol(node.get("tvol").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<List<StockKoreaVolumeRankResponse>> parseFVolumeRankKoreaForCategory(String response) {
        try {
            List<StockKoreaVolumeRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 6) {
                        break; // 6개까지만 추가
                    }

                    StockKoreaVolumeRankResponse responseData = new StockKoreaVolumeRankResponse();
                    responseData.setHtsKorIsnm(node.get("hts_kor_isnm").asText());
                    responseData.setMkscShrnIscd(node.get("mksc_shrn_iscd").asText());
                    responseData.setDataRank(node.get("data_rank").asText());
                    responseData.setStckPrpr(node.get("stck_prpr").asText());
                    responseData.setPrdyVrssSign(node.get("prdy_vrss_sign").asText());
                    responseData.setPrdyVrss(node.get("prdy_vrss").asText());
                    responseData.setPrdyCtrt(node.get("prdy_ctrt").asText());
                    responseData.setAcmlVol(node.get("acml_vol").asText());
                    responseData.setPrdyVol(node.get("prdy_vol").asText());
                    responseData.setLstnStcn(node.get("lstn_stcn").asText());
                    responseData.setAvrgVol(node.get("avrg_vol").asText());
                    responseData.setNBefrClprVrssPrprRate(
                            node.get("n_befr_clpr_vrss_prpr_rate").asText());
                    responseData.setVolInrt(node.get("vol_inrt").asText());
                    responseData.setVolTnrt(node.get("vol_tnrt").asText());
                    responseData.setNdayVolTnrt(node.get("nday_vol_tnrt").asText());
                    responseData.setAvrgTrPbmn(node.get("avrg_tr_pbmn").asText());
                    responseData.setTrPbmnTnrt(node.get("tr_pbmn_tnrt").asText());
                    responseData.setNdayTrPbmnTnrt(node.get("nday_tr_pbmn_tnrt").asText());
                    responseData.setAcmlTrPbmn(node.get("acml_tr_pbmn").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<List<StockKoreaVolumeRankResponse>> parseFVolumeRankKorea(String response) {
        try {
            List<StockKoreaVolumeRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 9) {
                        break; // 9개까지만 추가
                    }

                    StockKoreaVolumeRankResponse responseData = new StockKoreaVolumeRankResponse();
                    responseData.setHtsKorIsnm(node.get("hts_kor_isnm").asText());
                    responseData.setMkscShrnIscd(node.get("mksc_shrn_iscd").asText());
                    responseData.setDataRank(node.get("data_rank").asText());
                    responseData.setStckPrpr(node.get("stck_prpr").asText());
                    responseData.setPrdyVrssSign(node.get("prdy_vrss_sign").asText());
                    responseData.setPrdyVrss(node.get("prdy_vrss").asText());
                    responseData.setPrdyCtrt(node.get("prdy_ctrt").asText());
                    responseData.setAcmlVol(node.get("acml_vol").asText());
                    responseData.setPrdyVol(node.get("prdy_vol").asText());
                    responseData.setLstnStcn(node.get("lstn_stcn").asText());
                    responseData.setAvrgVol(node.get("avrg_vol").asText());
                    responseData.setNBefrClprVrssPrprRate(
                        node.get("n_befr_clpr_vrss_prpr_rate").asText());
                    responseData.setVolInrt(node.get("vol_inrt").asText());
                    responseData.setVolTnrt(node.get("vol_tnrt").asText());
                    responseData.setNdayVolTnrt(node.get("nday_vol_tnrt").asText());
                    responseData.setAvrgTrPbmn(node.get("avrg_tr_pbmn").asText());
                    responseData.setTrPbmnTnrt(node.get("tr_pbmn_tnrt").asText());
                    responseData.setNdayTrPbmnTnrt(node.get("nday_tr_pbmn_tnrt").asText());
                    responseData.setAcmlTrPbmn(node.get("acml_tr_pbmn").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<List<StockKoreaMarketCapResponse>> parseFMarketCapKorea(String response) {
        try {
            List<StockKoreaMarketCapResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 6) {
                        break; // 최대 6개의 데이터만 처리
                    }

                    StockKoreaMarketCapResponse responseData = new StockKoreaMarketCapResponse();
                    responseData.setMkscShrnIscd(node.get("mksc_shrn_iscd").asText());
                    responseData.setDataRank(node.get("data_rank").asText());
                    responseData.setHtsKorIsnm(node.get("hts_kor_isnm").asText());
                    responseData.setStckPrpr(node.get("stck_prpr").asText());
                    responseData.setPrdyVrss(node.get("prdy_vrss").asText());
                    responseData.setPrdyVrssSign(node.get("prdy_vrss_sign").asText());
                    responseData.setPrdyCtrt(node.get("prdy_ctrt").asText());
                    responseData.setAcmlVol(node.get("acml_vol").asText());
                    responseData.setLstnStcn(node.get("lstn_stcn").asText());
                    responseData.setStckAvls(node.get("stck_avls").asText());
                    responseData.setMrktWholAvlsRlim(node.get("mrkt_whol_avls_rlim").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("국내 시가총액 정보를 처리하는 중 오류가 발생했습니다."));
        }
    }

    private Mono<List<StockKoreaRisingDescentResponse>> parseFRisingDescentKorea(String response) {
        try {
            List<StockKoreaRisingDescentResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 6) {
                        break; // 최대 6개의 데이터만 처리
                    }

                    StockKoreaRisingDescentResponse responseData = new StockKoreaRisingDescentResponse();
                    responseData.setStckShrnIscd(node.get("stck_shrn_iscd").asText());
                    responseData.setDataRank(node.get("data_rank").asText());
                    responseData.setHtsKorIsnm(node.get("hts_kor_isnm").asText());
                    responseData.setStckPrpr(node.get("stck_prpr").asText());
                    responseData.setPrdyVrss(node.get("prdy_vrss").asText());
                    responseData.setPrdyVrssSign(node.get("prdy_vrss_sign").asText());
                    responseData.setPrdyCtrt(node.get("prdy_ctrt").asText());

                    responseDataList.add(responseData);
                    count++; // 추가된 데이터 개수 증가
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("국내 상승/하락 정보를 처리하는 중 오류가 발생했습니다.", e));
        }
    }

    public Mono<List<PriceInfo>> getItemChartPrice(Stock stock, String startDate, String endDate, String periodCode, COUNTRY country) {
        HttpHeaders headers = securityHttpConfig.createSecurityHeaders();

        if (country.equals(COUNTRY.KOREA)) {
            headers.set("tr_id", "FHKST03010100");

            return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                    .queryParam("fid_cond_mrkt_div_code", "J")
                    .queryParam("fid_input_iscd", stock.getSymbol())
                    .queryParam("fid_input_date_1", startDate)
                    .queryParam("fid_input_date_2", endDate)
                    .queryParam("fid_period_div_code", periodCode)
                    .queryParam("fid_org_adj_prc", "0") // 0: 수정주가, 1: 원주가
                    .build())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseFStockChartPriceKorea)
                .map(priceInfos -> priceInfos.stream().limit(100).toList());
        } else {
            headers.set("tr_id", "HHDFS76240000");

            String gubn = switch (periodCode) {
                case "D" -> "0";
                case "W" -> "1";
                case "M" -> "2";
                default -> "";
            };

            return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/dailyprice")
                    .queryParam("AUTH", "") // 사용자 권한 정보
                    .queryParam("EXCD", stock.getExchangeNum()) // 거래소 코드
                    .queryParam("SYMB", stock.getSymbol()) // 종목 코드
                    .queryParam("GUBN", gubn) // 일월주 구분
                    .queryParam("BYMD", endDate) // 조회 기준 일자
                    .queryParam("MODP", "1") // 수정 주가 반영 여부
                    .build())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseFStockChartPriceOverseas)
                .map(priceInfos -> priceInfos.stream().limit(100).toList());
        }
    }

    private Mono<List<PriceInfo>> parseFStockChartPriceKorea(String response) {
        List<PriceInfo> responseDataList = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                for (JsonNode node : outputNode) {
                    if(node.isEmpty()){
                        continue;
                    }

                    final PriceInfo responseData = PriceInfo.builder()
                        .closePrice(node.get("stck_clpr").asText()) // 종가
                        .openPrice(node.get("stck_oprc").asText()) // 시가
                        .highPrice(node.get("stck_hgpr").asText()) // 고가
                        .lowPrice(node.get("stck_lwpr").asText()) // 저가
                        .accumulatedTradingVolume(node.get("acml_vol").asText()) // 누적거래량
                        .accumulatedTradingValue(node.get("acml_tr_pbmn").asText()) // 누적거래대금
                        .localDate(node.get("stck_bsop_date").asText()) // 거래일
                        .build();

                    responseDataList.add(responseData);
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("국내 종목 정보가 없습니다."));
        }
    }

    private Mono<List<PriceInfo>> parseFStockChartPriceOverseas(String response) {
        List<PriceInfo> responseDataList = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                for (JsonNode node : outputNode) {
                    final PriceInfo responseData = PriceInfo.builder()
                        .closePrice(node.get("clos").asText()) // 종가
                        .openPrice(node.get("open").asText()) // 시가
                        .highPrice(node.get("high").asText()) // 고가
                        .lowPrice(node.get("low").asText()) // 저가
                        .accumulatedTradingVolume(node.get("tvol").asText()) // 누적거래량
                        .accumulatedTradingValue(node.get("tamt").asText()) // 누적거래대금
                        .localDate(node.get("xymd").asText()) // 거래일
                        .build();

                    responseDataList.add(responseData);
                }
            }

            return Mono.just(responseDataList);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("해외 종목 정보가 없습니다"));
        }
    }

    public Mono<StockInfoResponse> getRealTimeStockPrice(Stock stock) {
        return getSecurityStockInfoKorea(
                stock.getId(),
                stock.getSymbolName(),
                stock.getSecurityName(),
                stock.getSymbol(),
                stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum())
        );
    }

    /**
     * 거래소를 기반으로 국가를 판단하는 메서드
     */
    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangeNum) {
        return switch (exchangeNum) {
            case KOSPI, KOSDAQ, KOREAN_ETF -> COUNTRY.KOREA;
            default -> COUNTRY.OVERSEA;
        };
    }

}
