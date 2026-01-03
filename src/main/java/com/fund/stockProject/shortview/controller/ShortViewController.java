package com.fund.stockProject.shortview.controller;

import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.shortview.dto.ShortViewResponse;
import com.fund.stockProject.shortview.service.ShortViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.Parameter;

@Slf4j
@Tag(name = "숏뷰 (ShortView)", description = "숏뷰 추천 종목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/shortview")
@SecurityRequirement(name = "bearerAuth")
public class ShortViewController {

    private final ShortViewService shortViewService;

    @GetMapping
    @Operation(summary = "숏뷰 추천 종목 조회", description = "현재 사용자 선호/점수 데이터를 기반으로 5개의 추천 종목을 반환합니다.\n" +
            "가중치 기반 랜덤 선택으로 다양성을 확보하며, 중복을 방지합니다.\n" +
            "실시간 시세 조회 실패 시 가격 필드는 null로 반환됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShortViewResponse.class)))),
            @ApiResponse(responseCode = "204", description = "추천 가능한 종목 없음"),
            @ApiResponse(responseCode = "401", description = "인증 필요 또는 비회원")
    })
    public ResponseEntity<List<ShortViewResponse>> getRecommendations(
            @AuthenticationPrincipal @Parameter(hidden = true) CustomUserDetails userDetails
    ) {
        // 회원인 경우
        if (userDetails != null) {
            User currentUser = userDetails.getUser();
            log.info("회원(id:{})이 추천을 요청했습니다.", currentUser.getId());
            
            List<Integer> recommendedStockIds = shortViewService.getRecommendedStockIds(currentUser);
            
            if (recommendedStockIds.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            
            List<Stock> recommendedStocks = shortViewService.getStocksByIds(recommendedStockIds);
            log.info("회원(id:{})에게 주식 {}개를 추천했습니다.", currentUser.getId(), recommendedStocks.size());
            
            // 각 주식에 대해 실시간 가격 정보 조회
            List<ShortViewResponse> responses = recommendedStocks.stream()
                    .map(stock -> {
                        try {
                            var stockInfo = shortViewService.getRealTimeStockPriceSync(stock);
                            return ShortViewResponse.fromEntityWithPrice(stock, stockInfo);
                        } catch (Exception e) {
                            log.warn("실시간 가격 조회 실패, 기본 정보로 응답합니다. stock_id: {}, error: {}", 
                                    stock.getId(), e.getMessage());
                            return ShortViewResponse.fromEntity(stock);
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(responses);
        }

        // 비회원인 경우 401 Unauthorized 반환
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
