package com.fund.stockProject.experiment.controller;

import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusDetailResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
            description = """
                    사용자의 모든 실험 현황을 조회합니다. `/experiment/status`와 동일한 응답을 반환합니다.
                    
                    ### 응답 내용
                    - **progressExperiments**: 진행 중인 실험 목록 (5영업일 미만)
                    - **completeExperiments**: 완료된 실험 목록 (5영업일 경과 후 자동 매도)
                    - **avgRoi**: 전체 실험 평균 수익률
                    - **totalTradeCount**: 총 실험 수
                    - **progressTradeCount**: 진행 중인 실험 수
                    - **successRate**: 수익이 난 실험의 비율 (0.0 ~ 1.0)
                    """,
            tags = {"실험 (Experiment)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ExperimentStatusResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 유효하지 않음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<Mono<ExperimentStatusResponse>> getExperimentStatusAlias(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatus(customUserDetails));
    }

    @GetMapping("/status")
    @Operation(
            summary = "실험 현황 조회",
            description = """
                    사용자의 모든 실험 현황을 조회합니다.
                    
                    ### 주요 기능
                    - 진행 중인 실험과 완료된 실험을 분리하여 반환
                    - 각 실험의 현재 수익률, 점수, D-day 정보 포함
                    - 전체 통계 정보 제공 (평균 수익률, 성공률 등)
                    
                    ### D-day 계산
                    - 매수일 기준으로 영업일만 계산 (주말, 공휴일 제외)
                    - D-5 ~ D-1까지 표시, D-0이 되면 자동 매도
                    """,
            tags = {"실험 (Experiment)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = ExperimentStatusResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "progressExperiments": [
                                        {
                                          "experimentId": 1,
                                          "stockId": 115,
                                          "symbol": "TSLA",
                                          "symbolName": "테슬라",
                                          "buyDate": "25.11.01",
                                          "buyPrice": 250000,
                                          "currentPrice": 255000,
                                          "buyScore": 75,
                                          "currentScore": 80,
                                          "roiPercent": 2.0,
                                          "status": "PROGRESS",
                                          "autoSellIn": 3,
                                          "country": "OVERSEA"
                                        }
                                      ],
                                      "completeExperiments": [],
                                      "avgRoi": 2.0,
                                      "totalTradeCount": 1,
                                      "progressTradeCount": 1,
                                      "successRate": 0.0
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<Mono<ExperimentStatusResponse>> getExperimentStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatus(customUserDetails));
    }

    @GetMapping("/status/{experimentId}/detail")
    @Operation(
            summary = "실험 상세 정보 조회",
            description = """
                    특정 실험의 상세 정보를 조회합니다.
                    
                    ### 응답 내용
                    - 실험 기본 정보 (종목, 매수일, 매수가 등)
                    - 5영업일간의 점수 및 가격 변화 그래프 데이터
                    - 현재 수익률 및 점수 변화율
                    - D-day 정보
                    
                    ### 그래프 데이터
                    - **graphData**: 5일간의 일일 데이터 (D-5 ~ D-0)
                    - 각 데이터 포인트는 해당 영업일의 점수, 가격, ROI 포함
                    """,
            tags = {"실험 (Experiment)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = ExperimentStatusDetailResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "실험을 찾을 수 없음"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<Mono<ExperimentStatusDetailResponse>> getExperimentStatusDetail(
            @Parameter(
                    description = "실험 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable("experimentId") Integer experimentId) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatusDetail(experimentId));
    }

    @PostMapping("/{stockId}/buy/{country}")
    @Operation(
            summary = "종목 매수 (실험 시작)",
            description = """
                    지정된 종목을 모의 매수하여 실험을 시작합니다.
                    
                    ### 매수 프로세스
                    1. 현재 주가 조회 (KIS API)
                    2. 실험 데이터 생성 (상태: PROGRESS)
                    3. 매수일 점수 기록
                    4. 5영업일 후 자동 매도 스케줄링
                    
                    ### 매수 가격 결정
                    - 현재가를 우선 사용
                    - 현재가가 없으면 전일 종가 사용
                    - 둘 다 없으면 에러 반환
                    
                    ### 주의사항
                    - 동일 종목 중복 매수 가능
                    - 매수 후 즉시 실험 목록에 표시됨
                    - 5영업일 후 자동으로 매도 처리됨
                    """,
            tags = {"실험 (Experiment)"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "매수 성공",
                    content = @Content(
                            schema = @Schema(implementation = ExperimentSimpleResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "message": "모의 매수 성공",
                                      "price": 250000
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "매수 실패",
                    content = @Content(
                            schema = @Schema(implementation = ExperimentSimpleResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "message": "주가 정보를 가져올 수 없습니다",
                                      "price": 0
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "종목 또는 점수 정보를 찾을 수 없음")
    })
    public ResponseEntity<Mono<ExperimentSimpleResponse>> buyExperiment(
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
        return ResponseEntity.ok().body(experimentService.buyExperiment(customUserDetails, stockId, country));
    }

    @GetMapping("/report")
    @Operation(
            summary = "실험 결과 리포트 조회",
            description = """
                    완료된 실험들의 결과 리포트를 조회합니다.
                    
                    ### 응답 내용
                    - 완료된 실험 목록
                    - 종목별 수익률 및 점수 변화
                    - 실험 완료 날짜
                    
                    ### 사용 시점
                    - 실험 완료 후 결과 확인
                    - 히스토리 조회
                    - 리포트 생성
                    """,
            tags = {"실험 (Experiment)"}
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
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getReport(customUserDetails));
    }
}
