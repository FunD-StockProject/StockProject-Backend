package com.fund.stockProject.portfolio.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.portfolio.dto.PortfolioResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "포트폴리오 (Portfolio)", description = "투자 결과 및 분석 관련 API")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final ExperimentService experimentService;

    @GetMapping("/report")
    @Operation(
            summary = "실험 결과 리포트 조회 (Alias)",
            description = "/experiment/report와 동일한 응답을 반환합니다.",
            tags = {"포트폴리오 (Portfolio)"}
    )
    public ResponseEntity<Mono<ExperimentReportResponse>> getReport(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getReport(customUserDetails));
    }

    @GetMapping("/result")
    @Operation(
            summary = "포트폴리오 결과 조회",
            description = "사용자의 투자 포트폴리오 결과를 종합 분석하여 반환합니다. 점수 테이블, 인간지표, 투자 패턴, 히스토리 데이터를 포함합니다.",
            tags = {"포트폴리오 (Portfolio)"}
    )
    public ResponseEntity<Mono<PortfolioResultResponse>> getResult(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getPortfolioResult(customUserDetails));
    }
}


