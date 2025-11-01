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

    @GetMapping
    @Operation(summary = "실험(모의 매수) 현황 API (alias)", description = "실험(모의 매수) 현황 조회 - /experiment/status와 동일")
    public ResponseEntity<Mono<ExperimentStatusResponse>> getExperimentStatusAlias(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatus(customUserDetails));
    }

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

    // 개발 환경 테스트용 엔드포인트들 (프로덕션에서는 제거하거나 보안 강화 필요)
    @PostMapping("/test/trigger-auto-sell")
    @Operation(summary = "[테스트] 자동 매도 스케줄러 수동 실행", description = "5영업일 경과 실험 자동 매도 테스트용")
    public ResponseEntity<String> triggerAutoSell() {
        try {
            experimentService.triggerAutoSellForTest();
            return ResponseEntity.ok("자동 매도 스케줄러가 실행되었습니다. 로그를 확인하세요.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("에러: " + e.getMessage());
        }
    }

    @PostMapping("/test/trigger-progress-update")
    @Operation(summary = "[테스트] 진행중 실험 데이터 저장 스케줄러 수동 실행", description = "진행중 실험 데이터 저장 테스트용")
    public ResponseEntity<String> triggerProgressUpdate() {
        try {
            experimentService.triggerProgressUpdateForTest();
            return ResponseEntity.ok("진행중 실험 데이터 저장 스케줄러가 실행되었습니다. 로그를 확인하세요.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("에러: " + e.getMessage());
        }
    }

    @GetMapping("/test/business-days")
    @Operation(summary = "[테스트] 영업일 계산 테스트", description = "영업일 계산 로직 테스트용")
    public ResponseEntity<String> testBusinessDays() {
        try {
            return ResponseEntity.ok(experimentService.testBusinessDaysCalculation());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("에러: " + e.getMessage());
        }
    }

    @GetMapping("/test/comprehensive")
    @Operation(summary = "[테스트] 종합 테스트", description = "모든 경우의 수를 테스트합니다")
    public ResponseEntity<String> comprehensiveTest() {
        try {
            return ResponseEntity.ok(experimentService.runComprehensiveTests());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("에러: " + e.getMessage());
        }
    }
}
