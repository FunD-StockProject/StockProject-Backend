package com.fund.stockProject.score.dto.response;

import java.util.ArrayList;
import java.util.List;

import com.fund.stockProject.keyword.dto.KeywordDto;

import lombok.Getter;

@Getter
public class ScoreKeywordResponse {
        private final int finalScore;
        private final List<KeywordDto> topKeywords;

        public ScoreKeywordResponse(int finalScore, List<KeywordDto> topKeywords) {
            this.finalScore = finalScore;
            this.topKeywords = new ArrayList<>(topKeywords);
        }

}