package com.fund.stockProject.score.service;

import org.springframework.stereotype.Service;

import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;

    /**
     * 시퀀스를 입력받아 점수 정보를 반환합니다.
     * @param id 종목 시퀀스
     * @return 점수 정보
     */
    public ScoreResponse getScoreById(Integer id) {
        Score score = scoreRepository.findById(id).orElseThrow(() -> new RuntimeException("Score not found"));
        return new ScoreResponse(score.getScoreKorea(), score.getScoreOversea());
    }
}
