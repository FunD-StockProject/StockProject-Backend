package com.fund.stockProject.stock.controller;

import com.fund.stockProject.stock.domain.CATEGORY;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockCategoryResponse;
import com.fund.stockProject.stock.dto.response.StockChartResponse;
import com.fund.stockProject.stock.dto.response.StockDiffResponse;
import com.fund.stockProject.stock.dto.response.StockHotSearchResponse;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockRelevantResponse;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.dto.response.StockSimpleResponse;
import com.fund.stockProject.stock.service.StockService;

import io.swagger.v3.oas.annotations.Operation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

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
    ResponseEntity<Mono<StockInfoResponse>> getStockInfo(final @PathVariable("id") Integer id,
        final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getStockInfo(id, country));
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
}
