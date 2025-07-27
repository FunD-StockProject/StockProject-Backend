package com.fund.stockProject.experiment.controller;

import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.service.ExperimentService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/experiment")
public class ExperimentController {

    private ExperimentService experimentService;

    @GetMapping("/status")
    @Operation(summary = "실험(모의 매수) 현황 API", description = "실험(모의 매수) 현황 조회")
    public ResponseEntity<Mono<ExperimentStatusResponse>> getExperimentStatus(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        return ResponseEntity.ok().body(experimentService.getExperimentStatus(customUserDetails));
    }

    @PostMapping("/{id}/buy/{country}")
    @Operation(summary = "실험(모의 매수) 종목 매수 API", description = "실험(모의 매수) 종목 매수")
    public ResponseEntity<Mono<ExperimentSimpleResponse>> buyExperimentItem(@AuthenticationPrincipal CustomUserDetails customUserDetails, final @PathVariable("id") Integer stockId, final @PathVariable("country") String country) {
        return ResponseEntity.ok().body(experimentService.buyExperimentItem(customUserDetails, stockId, country));
    }

    @GetMapping("/search/{searchKeyword}/{country}")
    @Operation(summary = "관심 종목 검색 API", description = "주식 종목 및 인간지표 데이터 검색")
    public ResponseEntity<Mono<StockInfoResponse>> searchStockBySymbolName(final @PathVariable String searchKeyword, final @PathVariable String country) {
        return ResponseEntity.ok().body(experimentService.searchStockBySymbolName(searchKeyword, country));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "관심 종목 검색어 자동완성 API", description = "관심 종목 검색어 자동완성")
    public ResponseEntity<List<StockSearchResponse>> autocompleteKeyword(final @RequestParam String keyword) {
        return ResponseEntity.ok().body(experimentService.autoCompleteKeyword(keyword));
    }

}
