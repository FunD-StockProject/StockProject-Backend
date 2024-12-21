package com.fund.stockProject.keyword.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.keyword.service.KeywordService;
import com.fund.stockProject.stock.entity.Stock;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/keyword")
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping("/{keywordName}/stocks")
    @Operation(summary = "키워드 검색 API", description = "키워드 검색 API")
    public ResponseEntity<List<Stock>> getStocksByKeyword(@PathVariable String keywordName) {
        List<Stock> stocks = keywordService.findStocksByKeyword(keywordName);
        return ResponseEntity.ok(stocks);
    }
}