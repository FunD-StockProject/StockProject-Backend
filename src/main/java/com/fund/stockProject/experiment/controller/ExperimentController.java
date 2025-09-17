package com.fund.stockProject.experiment.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusDetailResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/experiment")
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping("/status")
    @Operation(summary = "실험(모의 매수) 현황 API", description = "실험(모의 매수) 현황 조회")
    public ResponseEntity<Mono<ExperimentStatusResponse>> getExperimentStatus(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatus(customUserDetails));
    }

    @GetMapping("/status/{experimentId}/detail")
    @Operation(summary = "실험(모의 매수) 현황 상세 보기 API", description = "실험(모의 매수) 현황 상세 보기")
    public ResponseEntity<Mono<ExperimentStatusDetailResponse>> getExperimentStatusDetail(@PathVariable("experimentId") Integer experimentId) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatusDetail(experimentId));
    }

    @PostMapping("/{stockId}/buy/{country}")
    @Operation(summary = "실험(모의 매수) 종목 매수 API", description = "실험(모의 매수) 종목 매수")
    public ResponseEntity<Mono<ExperimentSimpleResponse>> buyExperiment(@AuthenticationPrincipal CustomUserDetails customUserDetails, final @PathVariable("stockId") Integer stockId, final @PathVariable("country") String country) {
        return ResponseEntity.ok().body(experimentService.buyExperiment(customUserDetails, stockId, country));
    }

    @GetMapping("/report")
    @Operation(summary = "실험 결과 API", description = "실험 결과 조회")
    public ResponseEntity<Mono<ExperimentReportResponse>> getReport(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getReport(customUserDetails));
    }
}
