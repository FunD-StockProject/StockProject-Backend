package com.fund.stockProject.stock.service;


import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockKoreaVolumeRankResponse;
import com.fund.stockProject.stock.dto.response.StockOverseaVolumeRankResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final SecurityHttpConfig securityHttpConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * 국내, 해외 주식 정보 조회
     */
    public Mono<StockInfoResponse> getSecurityStockInfoKorea(String symbol, EXCHANGENUM exchangenum, COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return webClient.get()
                            .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                                         .queryParam("fid_cond_mrkt_div_code", "J")
                                                         .queryParam("fid_input_iscd", symbol)
                                                         .build())
                            .headers(httpHeaders -> {
                                HttpHeaders headers = securityHttpConfig.createSecurityHeaders(); // 항상 최신 헤더 가져오기
                                headers.set("tr_id", "FHKST01010100"); // 추가 헤더 설정
                                httpHeaders.addAll(headers);
                            })
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(response -> parseFStockInfoKorea(response, symbol, exchangenum));
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
                            .flatMap(response -> parseFStockInfoOversea(response, symbol, exchangenum));
        } else {
            return Mono.error(new UnsupportedOperationException("COUNTRY 입력 에러"));
        }
    }

    private Mono<StockInfoResponse> parseFStockInfoKorea(String response, String symbol, EXCHANGENUM exchangenum) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");
            StockInfoResponse stockInfoResponse = new StockInfoResponse();

            if (outputNode != null) {
                stockInfoResponse.setSymbol(symbol);
                stockInfoResponse.setExchange(exchangenum);
                stockInfoResponse.setPrice(outputNode.get("stck_prpr").asDouble());
                stockInfoResponse.setPriceDiff(outputNode.get("prdy_vrss").asDouble());
                stockInfoResponse.setPriceDiffPerCent(outputNode.get("prdy_ctrt").asDouble());
                stockInfoResponse.setPriceSign(outputNode.get("prdy_vrss_sign").asInt());
            }

            return Mono.just(stockInfoResponse);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("국내 종목 정보가 없습니다"));
        }
    }

    private Mono<StockInfoResponse> parseFStockInfoOversea(String response, String symbol, EXCHANGENUM exchangenum) {
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");
            StockInfoResponse stockInfoResponse = new StockInfoResponse();

            if (outputNode != null) {
                stockInfoResponse.setSymbol(symbol);
                stockInfoResponse.setExchange(exchangenum);
                stockInfoResponse.setPrice(outputNode.get("last").asDouble());
                // 해외는 diff가 절대값이므로 절대값에 따라 음수로 변경
                if(outputNode.get("rate").asDouble() < 0) {
                    stockInfoResponse.setPriceDiff(outputNode.get("diff").asDouble() * -1);
                }
                else{
                    stockInfoResponse.setPriceDiff(outputNode.get("diff").asDouble());
                }
                stockInfoResponse.setPriceDiffPerCent(outputNode.get("rate").asDouble());
                stockInfoResponse.setPriceSign(outputNode.get("sign").asInt());
            }

            return Mono.just(stockInfoResponse);
        } catch (Exception e) {
            return Mono.error(new UnsupportedOperationException("국내 종목 정보가 없습니다"));
        }
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

    // 현재는 나스닥기준, 추후 변경 예정
    private Mono<List<StockOverseaVolumeRankResponse>> parseFVolumeRankOversea(String response) {
        try {
            List<StockOverseaVolumeRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output2");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 3) break; // 3개까지만 추가

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

    private Mono<List<StockKoreaVolumeRankResponse>> parseFVolumeRankKorea(String response) {
        try {
            List<StockKoreaVolumeRankResponse> responseDataList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.get("output");

            if (outputNode != null) {
                int count = 0; // 추가된 데이터 개수 추적
                for (JsonNode node : outputNode) {
                    if (count >= 3) break; // 3개까지만 추가

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
                    responseData.setNBefrClprVrssPrprRate(node.get("n_befr_clpr_vrss_prpr_rate").asText());
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
}
