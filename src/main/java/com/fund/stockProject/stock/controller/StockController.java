package com.fund.stockProject.stock.controller;

import com.fund.stockProject.stock.domain.CATEGORY;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
import com.fund.stockProject.stock.dto.response.*;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.service.StockService;
import com.fund.stockProject.stock.service.SecurityService;
import com.fund.stockProject.shortview.dto.ShortViewResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
@Tag(name = "주식 (Stock)", description = "주식 정보 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class StockController {

    private final StockService stockService;
    private final SecurityService securityService;

    @GetMapping("/search/{searchKeyword}/{country}")
    @Operation(summary = "주식 종목 검색 API", description = "주식 종목 및 인간지표 데이터 검색")
    public ResponseEntity<Mono<StockInfoResponse>> searchStockBySymbolName(final @PathVariable String searchKeyword, final @PathVariable String country) {
        return ResponseEntity.ok().body(stockService.searchStockBySymbolName(searchKeyword, country));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "검색어 자동완성 API", description = "검색어 자동완성")
    public ResponseEntity<List<StockSearchResponse>> autocompleteKeyword(final @RequestParam String keyword) {
        return ResponseEntity.ok().body(stockService.autoCompleteKeyword(keyword));
    }

    @GetMapping("/hot/{country}")
    @Operation(summary = "지금 가장 hot한 지표 api", description = "지금 가장 hot한 지표 api")
    public ResponseEntity<Mono<List<StockSimpleResponse>>> getHotStocks(
        final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getHotStocks(country));
    }

    @GetMapping("/rising/{country}")
    @Operation(summary = "떡상중인 지표 api", description = "떡상중인 지표 api")
    public ResponseEntity<List<StockDiffResponse>> getRisingStocks(
        final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getRisingStocks(country));
    }

    @GetMapping("/descent/{country}")
    @Operation(summary = "떡락중인 지표 api", description = "떡락중인 지표 api")
    public ResponseEntity<List<StockDiffResponse>> getDescentStocks(
        final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getDescentStocks(country));
    }

    @GetMapping("/{id}/relevant")
    @Operation(summary = "관련 종목 api", description = "현재 종목과 관련된 종목 api")
    ResponseEntity<List<StockRelevantResponse>> getRelevantStocks(final @PathVariable("id") Integer id) {
        return ResponseEntity.ok().body(stockService.getRelevantStocks(id));
    }

    @GetMapping("/{id}/chart/{country}")
    @Operation(summary = "차트 정보 제공 api", description = "조회 종목의 날짜별 가격 변동 폭 정보 제공 api")
    ResponseEntity<Mono<StockChartResponse>> getStockChart(
        final @PathVariable("id") Integer id,
        final @RequestParam(required = false) String periodCode,
        final @RequestParam(required = false) LocalDate startDate,
        final @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok().body(stockService.getStockChart(id, periodCode, startDate, endDate));
    }

    @GetMapping("/{id}/info/{country}")
    @Operation(summary = "주식 정보 api", description = "주식 정보 api")
    ResponseEntity<StockDetailResponse> getStockInfo(final @PathVariable("id") Integer id,
                                                     final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getStockDetailInfo(id, country));
    }

    @GetMapping("/category/{category}/{country}")
    @Operation(summary = "종목 차트별 인간지표 api", description = "종목 차트별 인간지표 api")
    ResponseEntity<Mono<List<StockCategoryResponse>>> getCategoryStocks(final @PathVariable("category") CATEGORY category, final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getCategoryStocks(category, country));
    }

    @GetMapping("/rankings/hot")
    @Operation(summary = "인기검색어 api", description = "인기검색어 api")
    ResponseEntity<Mono<List<StockHotSearchResponse>>> getHotSearch() {
        return ResponseEntity.ok().body(stockService.getHotSearch());
    }

    @GetMapping("/summary/{symbol}/{country}")
    @Operation(summary = "종목 요약 api", description = "종목 요약 api")
    ResponseEntity<Mono<List<String>>> getSummarys(@PathVariable("symbol") String symbol, @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getSummarys(symbol, country));
    }

    @GetMapping("/sector/domestic/{sector}/recommend")
    @Operation(summary = "국내 섹터별 주식 추천", description = "특정 국내 섹터의 주식을 점수 기반으로 추천합니다. 실시간 시세 조회 실패 시 가격 필드는 null로 반환됩니다.")
    public ResponseEntity<ShortViewResponse> getRecommendationByDomesticSector(
            @io.swagger.v3.oas.annotations.Parameter(description = "추천할 국내 섹터", example = "RETAIL", required = true)
            @PathVariable String sector
    ) {
        try {
            // DomesticSector enum으로 변환
            DomesticSector sectorEnum;
            try {
                sectorEnum = DomesticSector.valueOf(sector.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("잘못된 DomesticSector 값: {}", sector);
                return ResponseEntity.badRequest().build();
            }

            log.info("DomesticSector({})별 추천을 요청했습니다.", sectorEnum);

            Stock recommendedStock = stockService.getRecommendedStockByDomesticSector(sectorEnum);
            if (recommendedStock != null) {
                log.info("DomesticSector({})에서 주식({})을 추천했습니다.", sectorEnum, recommendedStock.getSymbolName());
                
                // 실시간 가격 정보를 동기적으로 가져오기
                try {
                    var stockInfo = securityService.getRealTimeStockPrice(recommendedStock).block();
                    return ResponseEntity.ok(ShortViewResponse.fromEntityWithPrice(recommendedStock, stockInfo));
                } catch (Exception e) {
                    log.warn("실시간 가격 조회 실패, 기본 정보로 응답합니다. stock_id: {}, error: {}", 
                            recommendedStock.getId(), e.getMessage());
                    return ResponseEntity.ok(ShortViewResponse.fromEntity(recommendedStock));
                }
            } else {
                log.warn("DomesticSector({})에 대한 추천 주식을 찾을 수 없습니다. (유효한 주식이 없거나 점수가 없음)", sectorEnum);
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            log.error("DomesticSector별 추천 중 오류 발생: {}", sector, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/sector/overseas/{sector}/recommend")
    @Operation(summary = "해외 섹터별 주식 추천", description = "특정 해외 섹터의 주식을 점수 기반으로 추천합니다. 실시간 시세 조회 실패 시 가격 필드는 null로 반환됩니다.")
    public ResponseEntity<ShortViewResponse> getRecommendationByOverseasSector(
            @io.swagger.v3.oas.annotations.Parameter(description = "추천할 해외 섹터", example = "FINANCIALS", required = true)
            @PathVariable String sector
    ) {
        try {
            // OverseasSector enum으로 변환
            OverseasSector sectorEnum;
            try {
                sectorEnum = OverseasSector.valueOf(sector.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("잘못된 OverseasSector 값: {}", sector);
                return ResponseEntity.badRequest().build();
            }

            log.info("OverseasSector({})별 추천을 요청했습니다.", sectorEnum);

            Stock recommendedStock = stockService.getRecommendedStockByOverseasSector(sectorEnum);
            if (recommendedStock != null) {
                log.info("OverseasSector({})에서 주식({})을 추천했습니다.", sectorEnum, recommendedStock.getSymbolName());
                
                // 실시간 가격 정보를 동기적으로 가져오기
                try {
                    var stockInfo = securityService.getRealTimeStockPrice(recommendedStock).block();
                    return ResponseEntity.ok(ShortViewResponse.fromEntityWithPrice(recommendedStock, stockInfo));
                } catch (Exception e) {
                    log.warn("실시간 가격 조회 실패, 기본 정보로 응답합니다. stock_id: {}, error: {}", 
                            recommendedStock.getId(), e.getMessage());
                    return ResponseEntity.ok(ShortViewResponse.fromEntity(recommendedStock));
                }
            } else {
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            log.error("OverseasSector별 추천 중 오류 발생: {}", sector, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
