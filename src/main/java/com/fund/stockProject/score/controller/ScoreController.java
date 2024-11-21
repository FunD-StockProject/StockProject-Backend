package com.fund.stockProject.score.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.COUNTRY;

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
}
