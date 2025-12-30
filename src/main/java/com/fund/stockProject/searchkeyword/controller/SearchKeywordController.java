package com.fund.stockProject.searchkeyword.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse;
import com.fund.stockProject.searchkeyword.service.SearchKeywordService;
import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/search-keywords")
@RequiredArgsConstructor
public class SearchKeywordController {

    private final SearchKeywordService searchKeywordService;

    @GetMapping("/top")
    public ResponseEntity<List<SearchKeywordStatsResponse>> getTopSearchKeywords(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        List<SearchKeywordStatsResponse> topKeywords = searchKeywordService.getTopSearchKeywords(days, limit);
        return ResponseEntity.ok(topKeywords);
    }

    @GetMapping("/top/by-country")
    public ResponseEntity<List<SearchKeywordStatsResponse>> getTopSearchKeywordsByCountry(
            @RequestParam COUNTRY country,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "10") int limit) {
        List<SearchKeywordStatsResponse> topKeywords = searchKeywordService.getTopSearchKeywordsByCountry(country, days, limit);
        return ResponseEntity.ok(topKeywords);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getSearchCount(
            @RequestParam String keyword,
            @RequestParam COUNTRY country) {
        Long count = searchKeywordService.getSearchCount(keyword, country);
        return ResponseEntity.ok(count);
    }
}
