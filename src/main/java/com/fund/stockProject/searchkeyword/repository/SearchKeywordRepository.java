package com.fund.stockProject.searchkeyword.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
           "s.keyword, s.country, SUM(COALESCE(s.searchCount, 1))) " +
           "FROM SearchKeyword s " +
           "WHERE s.createdAt >= :startDate " +
           "GROUP BY s.keyword, s.country " +
           "ORDER BY SUM(COALESCE(s.searchCount, 1)) DESC")
    List<SearchKeywordStatsResponse> findTopSearchKeywords(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT new com.fund.stockProject.searchkeyword.dto.response.SearchKeywordStatsResponse(" +
           "s.keyword, s.country, SUM(COALESCE(s.searchCount, 1))) " +
           "FROM SearchKeyword s " +
           "WHERE s.createdAt >= :startDate AND s.country = :country " +
           "GROUP BY s.keyword, s.country " +
           "ORDER BY SUM(COALESCE(s.searchCount, 1)) DESC")
    List<SearchKeywordStatsResponse> findTopSearchKeywordsByCountry(
            @Param("startDate") LocalDateTime startDate,
            @Param("country") COUNTRY country);

    @Query("SELECT COALESCE(SUM(COALESCE(s.searchCount, 1)), 0) FROM SearchKeyword s " +
           "WHERE s.keyword = :keyword AND s.country = :country")
    Long sumSearchCountByKeywordAndCountry(
            @Param("keyword") String keyword,
            @Param("country") COUNTRY country);

    Optional<SearchKeyword> findTopByKeywordAndCountryAndCreatedAtBetweenOrderByIdAsc(
            String keyword,
            COUNTRY country,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}
