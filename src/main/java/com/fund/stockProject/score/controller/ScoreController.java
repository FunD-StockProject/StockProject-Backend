package com.fund.stockProject.score.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.service.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScoreController {
    private final ScoreService scoreService;

    @Operation(description = "종목페이지 - 인간지표 점수 조회")
    @GetMapping("{id}/score")
    public ResponseEntity<ScoreResponse> getScore(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(scoreService.getScoreById(id));
    }
}
