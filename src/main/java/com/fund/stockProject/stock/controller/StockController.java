package com.fund.stockProject.stock.controller;

import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
public class StockController {

    private final StockService stockService;

//    @ApiOperation("뉴스 게시글 작성")
//    @PostMapping("/post")
//    public ResponseEntity<String> postArticle(@RequestPart(value = "data", required = false) PostArticleReq postArticleReq){
//        articleService.postArticle(postArticleReq);
//        return ResponseEntity.ok("post success");
//    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello, Swagger!");
    }

    @GetMapping("/search/{symbolName}")
    @Operation(summary = "주식 종목 검색 API", description = "주식 종목 및 인간지표 데이터 검색")
    public ResponseEntity<StockSearchResponse> searchStockBySymbolName(final @PathVariable String symbolName) {
        StockSearchResponse stockSearchResponse = stockService.searchStockBySymbolName(symbolName);

        return ResponseEntity.ok().body(stockSearchResponse);
    }
}
