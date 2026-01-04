package com.fund.stockProject.experiment.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusDetailResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.dto.HumanIndicatorDistributionResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/experiment")
@Tag(name = "실험 (Experiment)", description = "모의 매수/매도 실험 관련 API")
@SecurityRequirement(name = "bearerAuth")
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping
    @Operation(
            summary = "실험 현황 조회 (Alias)",
            description = "/experiment/status와 동일한 응답을 반환합니다."
    )
    public ResponseEntity<ExperimentStatusResponse> getExperimentStatusAlias(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok(experimentService.getExperimentStatus(customUserDetails));
    }

    @GetMapping("/status")
    @Operation(
            summary = "실험 현황 조회",
            description = "사용자의 모든 실험 현황을 조회합니다. 진행 중인 실험과 완료된 실험을 분리하여 반환합니다."
    )
    public ResponseEntity<ExperimentStatusResponse> getExperimentStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok(experimentService.getExperimentStatus(customUserDetails));
    }

    @GetMapping("/status/{experimentId}/detail")
    @Operation(
            summary = "실험 상세 정보 조회",
            description = "특정 실험의 상세 정보 및 5영업일간의 점수/가격 변화 그래프 데이터를 조회합니다."
    )
    public ResponseEntity<ExperimentStatusDetailResponse> getExperimentStatusDetail(
            @Parameter(
                    description = "실험 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable("experimentId") Integer experimentId) {
        return ResponseEntity.ok(experimentService.getExperimentStatusDetail(experimentId));
    }

    @PostMapping("/{stockId}/buy/{country}")
    @Operation(
            summary = "종목 매수 (실험 시작)",
            description = "지정된 종목을 모의 매수하여 실험을 시작합니다. 5영업일 후 자동 매도됩니다."
    )
    public ResponseEntity<ExperimentSimpleResponse> buyExperiment(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(
                    description = "종목 ID",
                    required = true,
                    example = "115"
            )
            @PathVariable("stockId") Integer stockId,
            @Parameter(
                    description = "국가 코드 (KOREA: 국내, OVERSEA: 해외)",
                    required = true,
                    example = "OVERSEA",
                    schema = @Schema(allowableValues = {"KOREA", "OVERSEA"})
            )
            @PathVariable("country") String country) {
        return ResponseEntity.ok(experimentService.buyExperiment(customUserDetails, stockId, country));
    }

    @GetMapping("/report")
    @Operation(
            summary = "실험 결과 리포트 조회",
            description = "완료된 실험들의 결과 리포트를 조회합니다."
    )
    public ResponseEntity<ExperimentReportResponse> getReport(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok(experimentService.getReport(customUserDetails));
    }

    @GetMapping("/distribution")
    @Operation(
            summary = "인간지표 등급 분포 조회",
            description = "완료된 실험 1건 이상 유저 기준으로 인간지표 등급 비율을 조회합니다."
    )
    public ResponseEntity<HumanIndicatorDistributionResponse> getHumanIndicatorDistribution() {
        return ResponseEntity.ok(experimentService.getHumanIndicatorDistribution());
    }
}
