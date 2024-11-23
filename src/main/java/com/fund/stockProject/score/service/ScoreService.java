package com.fund.stockProject.score.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;

    /**
     * 주어진 stock_id와 country에 따라 Score 정보를 반환합니다.
     *
     * @param id      stock_id
     * @param country COUNTRY (KOREA or OVERSEA)
     * @return ScoreResponse
     */
    public ScoreResponse getScoreById(Integer id, COUNTRY country) {
        // 첫 인간지표인지 확인
        if (isFirst(id)) {
            // TODO: 첫 인간지표 로직 작성
            return ScoreResponse.builder()
                                .score(0) // 테스트값
                                .build();
        } else {
            // 첫 인간지표가 아닌 경우: 오늘 날짜 데이터를 조회
            LocalDate today = LocalDate.now();
            Score score = scoreRepository.findByStockIdAndDate(id, today)
                                         .orElseThrow(() -> new RuntimeException("Score not found for stock_id: " + id + " and date: " + today));

            // country에 따라 score_korea 또는 score_oversea 반환
            int scoreValue = (country == COUNTRY.KOREA) ? score.getScoreKorea() : score.getScoreOversea();

            return ScoreResponse.builder()
                                .score(scoreValue)
                                .build();
        }
    }

    /**
     * stock_id와 country에 따라 첫 인간지표 여부를 확인합니다.
     *
     * @param id      stock_id
     * @return true if first indicator, otherwise false
     */
    private boolean isFirst(Integer id) {
        // 1111-11-11 날짜의 데이터가 존재하면 첫 인간지표로 판단
        return scoreRepository.existsByStockIdAndDate(id, LocalDate.of(1111, 11, 11));
    }
}
