package com.fund.stockProject.searchkeyword.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse;
import com.fund.stockProject.searchkeyword.entity.SearchKeyword;
import com.fund.stockProject.stock.domain.COUNTRY;

@Repository
public interface SearchKeywordRepository extends JpaRepository<SearchKeyword, Long> {

    @Query("SELECT new com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse(" +
           "s.keyword, s.country, COUNT(s)) " +
           "FROM SearchKeyword s " +
           "WHERE s.createdAt >= :startDate " +
           "GROUP BY s.keyword, s.country " +
           "ORDER BY COUNT(s) DESC")
    List<SearchKeywordStatsResponse> findTopSearchKeywords(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT new com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse(" +
           "s.keyword, s.country, COUNT(s)) " +
           "FROM SearchKeyword s " +
           "WHERE s.createdAt >= :startDate AND s.country = :country " +
           "GROUP BY s.keyword, s.country " +
           "ORDER BY COUNT(s) DESC")
    List<SearchKeywordStatsResponse> findTopSearchKeywordsByCountry(
            @Param("startDate") LocalDateTime startDate,
            @Param("country") COUNTRY country);

    Long countByKeywordAndCountry(String keyword, COUNTRY country);
}
