package com.fund.stockProject.stock.controller;

import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.dto.response.StockSimpleResponse;
import com.fund.stockProject.stock.service.StockService;

import io.swagger.v3.oas.annotations.Operation;

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

    @GetMapping("/search/{symbolName}")
    @Operation(summary = "주식 종목 검색 API", description = "주식 종목 및 인간지표 데이터 검색")
    public ResponseEntity<StockSearchResponse> searchStockBySymbolName(final @PathVariable String symbolName) {
        return ResponseEntity.ok().body(stockService.searchStockBySymbolName(symbolName));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "검색어 자동완성 API", description = "검색어 자동완성")
    public ResponseEntity<List<StockSearchResponse>> autocompleteKeyword(final @RequestParam String keyword) {
        return ResponseEntity.ok().body(stockService.autoCompleteKeyword(keyword));
    }

    @GetMapping("/hot/{country}")
    @Operation(summary = "지금 가장 hot한 지표 api", description = "지금 가장 hot한 지표 api")
    public ResponseEntity<Mono<List<StockSimpleResponse>>> getHotStocks(final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getHotStocks(country));
    }

    @GetMapping("/rising/{country}")
    @Operation(summary = "떡상중인 지표 api", description = "떡상중인 지표 api")
    public ResponseEntity<List<StockSimpleResponse>> getRisingStocks(final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getRisingStocks(country));
    }

    @GetMapping("/descent/{country}")
    @Operation(summary = "떡락중인 지표 api", description = "떡락중인 지표 api")
    public ResponseEntity<List<StockSimpleResponse>> getDescentStocks(final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(stockService.getDescentStocks(country));
    }
}
