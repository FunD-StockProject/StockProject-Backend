package com.fund.stockProject.score.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.score.service.ScoreBatchService;
import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/batch/score")
@RequiredArgsConstructor
public class ScoreBatchController {

    private final ScoreBatchService scoreBatchService;

    @PostMapping("/country/{country}")
    public ResponseEntity<String> triggerCountryBatch(@PathVariable COUNTRY country) {
        scoreBatchService.runCountryBatch(country);
        return ResponseEntity.ok("Batch for " + country + " started.");
    }

    @PostMapping("/index")
    public ResponseEntity<String> triggerIndexBatch() {
        scoreBatchService.runIndexBatch();
        return ResponseEntity.ok("Index batch started.");
    }
}
