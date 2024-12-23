package com.fund.stockProject.score.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.keyword.dto.KeywordDto;
import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockWordResponse;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScoreController {
    private final ScoreService scoreService;

    @Operation(summary = "종목페이지 - 인간지표 점수 조회", description = "종목페이지 - 인간지표 점수 조회")
    @GetMapping("{id}/score/{country}")
    public ResponseEntity<ScoreResponse> getScore(@PathVariable("id") Integer id, final @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok(scoreService.getScoreById(id, country));
    }

    @GetMapping("/wordcloud/{symbol}/{country}")
    @Operation(summary = "워드클라우드 api", description = "워드클라우드 api")
    ResponseEntity<List<StockWordResponse>> getWordCloud(@PathVariable("symbol") String symbol, @PathVariable("country") COUNTRY country) {
        return ResponseEntity.ok().body(scoreService.getWordCloud(symbol, country));
    }

    @GetMapping("{id}/keywords")
    public ResponseEntity<List<KeywordDto>> getKeywordsByStock(@PathVariable Integer id) {
        List<KeywordDto> keywords = scoreService.getKeywordsByStock(id);
        return ResponseEntity.ok(keywords);
    }
}
