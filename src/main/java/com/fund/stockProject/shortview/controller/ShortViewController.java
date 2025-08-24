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

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/shortview")
public class ShortViewController {

    private final ShortViewService shortViewService;

    @GetMapping
    public ResponseEntity<ShortViewResponse> getRecommendation(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 회원인 경우
        if (userDetails != null) {
            User currentUser = userDetails.getUser();
            log.info("회원(id:{})이 추천을 요청했습니다.", currentUser.getId());

            Stock recommendedStock = shortViewService.getRecommendedStock(currentUser);
            if (recommendedStock != null) {
                log.info("회원(id:{})에게 주식({})을 추천했습니다.",
                        currentUser.getId(), recommendedStock.getSymbolName());
                
                // 실시간 가격 정보를 동기적으로 가져오기
                try {
                    var stockInfo = shortViewService.getRealTimeStockPriceSync(recommendedStock);
                    return ResponseEntity.ok(ShortViewResponse.fromEntityWithPrice(recommendedStock, stockInfo));
                } catch (Exception e) {
                    log.warn("실시간 가격 조회 실패, 기본 정보로 응답합니다. stock_id: {}, error: {}", 
                            recommendedStock.getId(), e.getMessage());
                    return ResponseEntity.ok(ShortViewResponse.fromEntity(recommendedStock));
                }
            } else {
                return ResponseEntity.noContent().build();
            }
        }

        // 비회원인 경우 401 Unauthorized 반환
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}