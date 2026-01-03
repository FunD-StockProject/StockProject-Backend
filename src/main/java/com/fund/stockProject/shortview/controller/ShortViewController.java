package com.fund.stockProject.shortview.controller;

import com.fund.stockProject.stock.dto.response.StockInfoResponse;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            
            final int recommendTargetCount = 5;
            List<Integer> recommendedStockIds = shortViewService.getRecommendedStockIds(currentUser);
            
            if (recommendedStockIds.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            
            List<Stock> recommendedStocks = shortViewService.getStocksByIds(recommendedStockIds);
            Collections.shuffle(recommendedStocks);
            log.info("회원(id:{})에게 주식 {}개를 추천했습니다.", currentUser.getId(), recommendedStocks.size());

            List<Integer> recommendedIds = recommendedStocks.stream()
                    .map(Stock::getId)
                    .toList();
            var latestScoreMap = shortViewService.getLatestScoresByStockIds(recommendedIds);
            var keywordsByStockId = shortViewService.getKeywordsByStockIds(recommendedIds, 3);

            int priceConcurrency = 8;
            List<ShortViewResponse> responses = Flux.fromIterable(recommendedStocks)
                    .flatMap(stock ->
                            shortViewService.getRealTimeStockPrice(stock)
                                    .timeout(Duration.ofMillis(800))
                                    .filter(stockInfo -> {
                                        boolean valid = isValidPriceInfo(stockInfo);
                                        if (!valid) {
                                            log.warn("유효하지 않은 가격 정보로 제외합니다. stock_id: {}", stock.getId());
                                        }
                                        return valid;
                                    })
                                    .map(stockInfo -> ShortViewResponse.fromEntityWithPrice(
                                            stock,
                                            stockInfo,
                                            latestScoreMap.get(stock.getId()),
                                            keywordsByStockId.getOrDefault(stock.getId(), List.of())
                                    ))
                                    .onErrorResume(e -> {
                                        log.warn("실시간 가격 조회 실패로 제외합니다. stock_id: {}, error: {}", 
                                                stock.getId(), e.getMessage());
                                        return Mono.empty();
                                    }),
                            priceConcurrency
                    )
                    .take(recommendTargetCount)
                    .collectList()
                    .block();

            if (responses == null) {
                responses = new ArrayList<>();
            }

            if (responses.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            
            return ResponseEntity.ok(responses);
        }

        // 비회원인 경우 401 Unauthorized 반환
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private boolean isValidPriceInfo(StockInfoResponse stockInfo) {
        if (stockInfo == null) {
            return false;
        }
        Double price = stockInfo.getPrice();
        Double priceDiff = stockInfo.getPriceDiff();
        Double priceDiffPerCent = stockInfo.getPriceDiffPerCent();
        if (price == null || price <= 0 || !Double.isFinite(price)) {
            return false;
        }
        if (priceDiff == null || !Double.isFinite(priceDiff)) {
            return false;
        }
        if (priceDiffPerCent == null || !Double.isFinite(priceDiffPerCent)) {
            return false;
        }
        return true;
    }
}
