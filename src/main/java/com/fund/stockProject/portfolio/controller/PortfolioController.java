package com.fund.stockProject.portfolio.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.portfolio.dto.PortfolioResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/portfolio")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final ExperimentService experimentService;

    @GetMapping("/report")
    @Operation(summary = "실험 결과 리포트 (alias)", description = "/experiment/report와 동일 응답")
    public ResponseEntity<Mono<ExperimentReportResponse>> getReport(
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getReport(customUserDetails));
    }

    @GetMapping("/result")
    @Operation(summary = "실험 결과(포트폴리오)", description = "LabResult mock 구조를 기반으로 한 결과 응답")
    public ResponseEntity<Mono<PortfolioResultResponse>> getResult(
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getPortfolioResult(customUserDetails));
    }
}


