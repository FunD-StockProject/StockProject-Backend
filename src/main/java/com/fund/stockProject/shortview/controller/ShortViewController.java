package com.fund.stockProject.shortview.controller;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.shortview.dto.ShortViewResponse;
import com.fund.stockProject.shortview.service.ShortViewService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 숏뷰 추천 API 컨트롤러 (서비스 로직에 맞춰 수정됨)
 *
 * 🔗 API 엔드포인트:
 * - GET /shortview: 추천 주식 정보 (회원: 개인화 추천, 비회원: 인기 주식 추천)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/shortview")
public class ShortViewController {

    private final ShortViewService shortViewService;

    /**
     * 추천 주식 API
     *
     * 🎯 회원용: 개인화된 추천을 제공합니다.
     * � 비회원용: 인기 주식을 순서대로 추천하며, 세션당 5회로 제한됩니다.
     */
    @GetMapping
    public Mono<ResponseEntity<ShortViewResponse>> getRecommendation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpSession session
    ) {
        // 회원인 경우
        if (userDetails != null) {
            User currentUser = userDetails.getUser();
            log.info("회원(id:{})이 추천을 요청했습니다.", currentUser.getId());

            return shortViewService.getRecommendedStockWithPrice(currentUser)
                    .map(response -> {
                        log.info("회원(id:{})에게 주식({})을 추천했습니다.",
                                currentUser.getId(), response.getStockName());
                        return ResponseEntity.ok(response);
                    })
                    .switchIfEmpty(Mono.just(ResponseEntity.noContent().build()));
        }

        // 비회원인 경우
        String sessionId = session.getId();
        Integer requestCount = (Integer) session.getAttribute("nonMemberRequestCount");
        if (requestCount == null) {
            requestCount = 0;
        }

        // 5회 초과 요청 시 429 Too Many Requests 반환
        if (requestCount >= 5) {
            log.warn("비회원(session:{})이 5회를 초과하여 요청했습니다.", sessionId);
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
        }

        requestCount++;
        session.setAttribute("nonMemberRequestCount", requestCount);

        log.info("비회원(session:{})이 인기 주식을 요청했습니다. (요청 횟수: {})", sessionId, requestCount);

        // [수정됨] getPopularStockWithPrice 메서드가 없으므로, getPopularStock을 호출하도록 변경합니다.
        // 이 경우 실시간 가격 정보는 포함되지 않습니다.
        return Mono.fromCallable(() -> shortViewService.getPopularStock(null))
                .map(popularStock -> {
                    if (popularStock != null) {
                        log.info("비회원(session:{})에게 인기 주식({})을 추천했습니다.",
                                sessionId, popularStock.getSymbolName());
                        // 가격 정보 없이 응답 DTO를 생성합니다.
                        return ResponseEntity.ok(ShortViewResponse.fromEntity(popularStock));
                    } else {
                        // 추천할 주식이 없는 경우
                        return ResponseEntity.noContent().<ShortViewResponse>build();
                    }
                });
    }
}