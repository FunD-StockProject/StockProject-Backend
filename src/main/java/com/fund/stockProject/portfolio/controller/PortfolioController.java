package com.fund.stockProject.portfolio.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.portfolio.dto.PortfolioResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
            description = """
                    `/experiment/report`와 동일한 응답을 반환합니다.
                    완료된 실험들의 결과 리포트를 조회합니다.
                    """,
            tags = {"포트폴리오 (Portfolio)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ExperimentReportResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<Mono<ExperimentReportResponse>> getReport(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getReport(customUserDetails));
    }

    @GetMapping("/result")
    @Operation(
            summary = "포트폴리오 결과 조회",
            description = """
                    사용자의 투자 포트폴리오 결과를 종합 분석하여 반환합니다.
                    
                    ### 응답 구조
                    - **scoreTable**: 점수 구간별 평균 수익률 테이블
                    - **experimentSummary**: 실험 요약 정보 (총 실험 수, 최고/최저 수익)
                    - **humanIndex**: 인간지표 분석 (성공률, 유형, 등급 비율)
                    - **investmentPattern**: 투자 패턴 분석 (사분면 기반 분류)
                    - **history**: 날짜별 평균 점수와 ROI 히스토리 (차트 데이터)
                    
                    ### 인간지표 유형
                    - **완전 인간 아님**: 성공률 0~20%
                    - **인간 아님**: 성공률 21~40%
                    - **평범 인간**: 성공률 41~60%
                    - **인간 맞음**: 성공률 61~80%
                    - **인간 완전 맞음**: 성공률 81~100%
                    
                    ### 투자 패턴
                    - **가치 선점형**: ROI > 0, 점수 < 평균 (낮은 점수에 매수하여 수익)
                    - **트렌드 선점형**: ROI > 0, 점수 >= 평균 (높은 점수에 매수하여 수익)
                    - **역행 투자형**: ROI <= 0, 점수 < 평균 (낮은 점수에 매수했으나 손실)
                    - **후행 추종형**: ROI <= 0, 점수 >= 평균 (높은 점수에 매수했으나 손실)
                    
                    ### 히스토리 데이터
                    - 날짜별로 그룹화된 평균 점수(x축)와 ROI(y축)
                    - 사분면 차트에 표시되는 데이터 포인트
                    """,
            tags = {"포트폴리오 (Portfolio)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = PortfolioResultResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "scoreTable": [
                                        {"range": "0~20", "avg": -5.2, "median": null},
                                        {"range": "21~40", "avg": 2.1, "median": null},
                                        {"range": "41~60", "avg": 5.8, "median": null},
                                        {"range": "61~80", "avg": 8.3, "median": null},
                                        {"range": "81~100", "avg": 12.5, "median": null}
                                      ],
                                      "experimentSummary": {
                                        "totalExperiments": 15,
                                        "highestProfit": {"score": 85, "range": "81~100"},
                                        "lowestProfit": {"score": 25, "range": "21~40"}
                                      },
                                      "humanIndex": {
                                        "userScore": 65,
                                        "userType": "인간 맞음",
                                        "successRate": "61~80%",
                                        "maintainRate": null,
                                        "purchasedCount": 15,
                                        "profitCount": 10,
                                        "sameGradeUserRate": 25
                                      },
                                      "investmentPattern": {
                                        "patternType": "트렌드 선점형",
                                        "patternDescription": "점수가 높을 때 매수하여 수익을 보는 투자 패턴",
                                        "avgScore": 65.5
                                      },
                                      "history": [
                                        {"x": 70, "y": 5.2, "label": "1101"},
                                        {"x": 65, "y": 3.1, "label": "1102"},
                                        {"x": 68, "y": 4.5, "label": "1103"}
                                      ]
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<Mono<PortfolioResultResponse>> getResult(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        return ResponseEntity.ok().body(experimentService.getPortfolioResult(customUserDetails));
    }
}


