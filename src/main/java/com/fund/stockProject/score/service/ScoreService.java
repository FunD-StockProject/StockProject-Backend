package com.fund.stockProject.score.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.keyword.dto.KeywordDto;
import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.keyword.repository.StockKeywordRepository;
import com.fund.stockProject.score.dto.response.ScoreKeywordResponse;
import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockWordResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final StockRepository stockRepository;
    private final KeywordRepository keywordRepository;
    private final StockKeywordRepository stockKeywordRepository;

    @Transactional(readOnly = true)
    public List<Score> findScoresByDate(LocalDate yesterday, LocalDate today) {
        return scoreRepository.findScoresWithoutTodayData(yesterday, today);
    }

    public ScoreResponse getScoreById(Integer id, COUNTRY country) {
        // 첫 인간지표인경우
        if (isFirst(id)) {
            Stock stock = stockRepository.findById(id).orElseThrow(() -> new RuntimeException("Could not find stock"));
            // STEP1: AI 실행
            int finalScore = executeScoreAI(stock.getSymbol(), country);

            // STEP2: SCORE 데이터 저장
            Score newScore = Score.builder()
                                  .stockId(stock.getId())
                                  .date(LocalDate.now())
                                  .scoreKorea(country == COUNTRY.KOREA? finalScore : 9999)
                                  .scoreNaver(finalScore)
                                  .scoreReddit(9999)
                                  .scoreOversea(country == COUNTRY.OVERSEA? finalScore : 9999)
                                  .diff(0)
                                  .build();
            // `stock` 연관 설정
            newScore.setStock(stock);
            scoreRepository.save(newScore);

            // STEP3: 기존의 초기 데이터 삭제 (1111-11-11)
            scoreRepository.deleteByStockIdAndDate(id, LocalDate.of(1111, 11, 11));

            return ScoreResponse.builder()
                                .score(finalScore)
                                .build();
        } else {
            // 첫 인간지표가 아닌 경우: 오늘 날짜 데이터를 조회
            LocalDate today = LocalDate.now();
            Score score = scoreRepository.findByStockIdAndDate(id, today)
                                         .orElseGet(() -> {
                                             // 오늘 데이터가 없으면 하루 전 날짜 데이터 조회
                                             LocalDate yesterday = today.minusDays(1);
                                             return scoreRepository.findByStockIdAndDate(id, yesterday)
                                                                   .orElseThrow(() -> new RuntimeException("Score not found for stock_id: " + id + " and date: " + today + " or " + yesterday));
                                         });

            // country에 따라 점수 선택
            int scoreValue = (country == COUNTRY.KOREA) ? score.getScoreKorea() : score.getScoreOversea();

            return ScoreResponse.builder()
                                .score(scoreValue)
                                .build();
        }
    }


    public List<StockWordResponse> getWordCloud(final String symbol, final COUNTRY country) {
        try {
            String scriptPath = "wc.py";

            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol, country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Asynchronously read the script output
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines()
                             .filter(line -> line.trim().startsWith("{") && line.trim().endsWith("}")) // JSON format filtering
                             .collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // Maximum 60 seconds wait

            // Check the process exit code
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script execution failed with exit code: " + exitCode);
            }

            // Parse the JSON output from the Python script
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(output);

            // Parse the "word_cloud" array
            List<StockWordResponse> wordCloud = new ArrayList<>();
            for (JsonNode wordNode : jsonNode.get("word_cloud")) {
                String word = wordNode.get("word").asText();
                int freq = wordNode.get("freq").asInt();
                wordCloud.add(new StockWordResponse(word, freq));
            }

            return wordCloud;

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Python script", e);
        }
    }

    public void updateScore(Integer id, COUNTRY country, int yesterdayScore) {
        Stock stock = stockRepository.findById(id).orElseThrow(() -> new RuntimeException("Could not find stock"));
        // STEP1: AI 실행
        int finalScore = executeScoreAI(stock.getSymbol(), country);

        // STEP2: SCORE 데이터 저장
        Score newScore = Score.builder()
                              .stockId(stock.getId())
                              .date(LocalDate.now())
                              .scoreKorea(country == COUNTRY.KOREA? finalScore : 9999)
                              .scoreNaver(finalScore)
                              .scoreReddit(9999)
                              .scoreOversea(country == COUNTRY.OVERSEA? finalScore : 9999)
                              .diff(finalScore - yesterdayScore)
                              .build();
        // `stock` 연관 설정
        newScore.setStock(stock);
        scoreRepository.save(newScore);

    }

    // TODO: 키워드 업데이트 로직 개발 중
    @Transactional
    public void updateScoreAndKeyword(Integer id, COUNTRY country, int yesterdayScore) {
        Stock stock = stockRepository.findById(id)
                                     .orElseThrow(() -> new RuntimeException("Could not find stock"));
        try {
            // Python 스크립트를 실행하여 결과 가져오기
            ScoreKeywordResponse scoreKeywordResponse = executeUpdateAI(stock.getSymbol(), country);

            // STEP2: SCORE 데이터 저장
            int finalScore = scoreKeywordResponse.getFinalScore();
            Score newScore = Score.builder()
                                  .stockId(stock.getId())
                                  .date(LocalDate.now())
                                  .scoreKorea(country == COUNTRY.KOREA? finalScore : 9999)
                                  .scoreNaver(finalScore)
                                  .scoreReddit(9999)
                                  .scoreOversea(country == COUNTRY.OVERSEA? finalScore : 9999)
                                  .diff(finalScore - yesterdayScore)
                                  .build();

            // `stock` 연관 설정
            newScore.setStock(stock);
            scoreRepository.save(newScore);

            // 기존 StockKeyword 삭제
            stockKeywordRepository.deleteByStock(stock);
            System.out.println("scoreKeywordResponse.getTopKeywords().get(0).getWord() = " + scoreKeywordResponse.getTopKeywords().get(0).getWord());
            scoreKeywordResponse.getTopKeywords().forEach(keywordDto -> {
                Keyword newKeyword = Keyword.builder()
                                            .name(keywordDto.getWord())
                                            .frequency(keywordDto.getFreq())
                                            .build();

                newKeyword.updateFrequency(keywordDto.getFreq());
                keywordRepository.save(newKeyword);

                // StockKeyword 테이블에 매핑 정보 저장
                StockKeyword stockKeyword = new StockKeyword(stock, newKeyword);
                stockKeywordRepository.save(stockKeyword);
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to update score and keyword", e);
        }
    }

    private boolean isFirst(Integer id) {
        // 1111-11-11 날짜의 데이터가 존재하면 첫 인간지표로 판단
        return scoreRepository.existsByStockIdAndDate(id, LocalDate.of(1111, 11, 11));
    }

    private int executeScoreAI(String symbol, COUNTRY country) {
        try {
            // Python 스크립트 경로
            String scriptPath = "score.py";

            // ProcessBuilder를 사용하여 Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol, country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 비동기 스트림 읽기
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines()
                             .filter(line -> line.trim().startsWith("{") && line.trim().endsWith("}")) // JSON 형식 필터링
                             .collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // 최대 120초 대기

            // 프로세스 종료 코드 확인
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script execution failed with exit code: " + exitCode);
            }

            // Python 스크립트에서 출력된 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(output);
            return jsonNode.get("final_score").asInt();

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Python script", e);
        }
    }

    private ScoreKeywordResponse executeUpdateAI(String symbol, COUNTRY country) {
        try {
            // Python 스크립트 경로
            String scriptPath = "update.py";

            // ProcessBuilder를 사용하여 Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol, country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 비동기 스트림 읽기
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines().collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // 최대 60초 대기

            // 프로세스 종료 코드 확인
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script execution failed with exit code: " + exitCode);
            }

            // Python 스크립트에서 출력된 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(output);

            int finalScore = jsonNode.get("final_score").asInt();
            List<KeywordDto> topKeywords = new ArrayList<>();
            for (JsonNode keywordNode : jsonNode.get("top_keywords")) {
                String word = keywordNode.get("word").asText();
                int freq = keywordNode.get("freq").asInt();
                topKeywords.add(new KeywordDto(word, freq));
            }

            return new ScoreKeywordResponse(finalScore, topKeywords);

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Python script", e);
        }
    }

    @Transactional(readOnly = true)
    public List<KeywordDto> getKeywordsByStock(Integer stockId) {
        Stock stock = stockRepository.findById(stockId)
                                     .orElseThrow(() -> new RuntimeException("Stock not found"));

        return stockKeywordRepository.findByStock(stock)
                                     .stream()
                                     .map(stockKeyword -> new KeywordDto(stockKeyword.getKeyword().getName(),
                                                                         stockKeyword.getKeyword().getFrequency()))
                                     .collect(Collectors.toList());
    }
}
