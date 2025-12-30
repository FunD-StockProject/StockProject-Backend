package com.fund.stockProject.searchkeyword.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse;
import com.fund.stockProject.searchkeyword.entity.SearchKeyword;
import com.fund.stockProject.searchkeyword.repository.SearchKeywordRepository;
import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordService {

    private final SearchKeywordRepository searchKeywordRepository;

    @Async
    @Transactional
    public void saveSearchKeyword(String keyword, COUNTRY country) {
        try {
            SearchKeyword searchKeyword = SearchKeyword.of(keyword, country);
            searchKeywordRepository.save(searchKeyword);
            log.debug("Saved search keyword: {} ({})", keyword, country);
        } catch (Exception e) {
            log.error("Failed to save search keyword: {} ({})", keyword, country, e);
        }
    }

    @Transactional(readOnly = true)
    public List<SearchKeywordStatsResponse> getTopSearchKeywords(int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return searchKeywordRepository.findTopSearchKeywords(startDate)
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SearchKeywordStatsResponse> getTopSearchKeywordsByCountry(COUNTRY country, int days, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return searchKeywordRepository.findTopSearchKeywordsByCountry(startDate, country)
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public Long getSearchCount(String keyword, COUNTRY country) {
        return searchKeywordRepository.countByKeywordAndCountry(keyword, country);
    }
}
