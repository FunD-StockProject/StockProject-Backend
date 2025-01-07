package com.fund.stockProject.keyword.controller;

import com.fund.stockProject.keyword.service.KeywordService;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/keyword")
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping("/{keywordName}/stocks")
    @Operation(summary = "키워드 검색 API", description = "키워드 검색 API")
    public ResponseEntity<List<StockInfoResponse>> getStocksByKeyword(@PathVariable String keywordName) {
        final List<StockInfoResponse> stocksByKeyword = keywordService.findStocksByKeyword(keywordName);
        return ResponseEntity.ok(stocksByKeyword);
    }

    @GetMapping("/popular/{country}")
    @Operation(summary = "자주 언급되는 키워드 조회 API", description = "자주 언급되는 키워드 조회 API")
    public ResponseEntity<List<String>> getPopularKeyword(@PathVariable("country") COUNTRY country) {
        final List<String> popularKeywords = keywordService.findPopularKeyword(country);
        return ResponseEntity.ok(popularKeywords);
    }
}
