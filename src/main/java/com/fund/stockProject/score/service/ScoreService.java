package com.fund.stockProject.score.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.keyword.dto.KeywordDto;
import com.fund.stockProject.keyword.repository.StockKeywordRepository;
import com.fund.stockProject.score.dto.response.ScoreIndexResponse;
import com.fund.stockProject.score.dto.response.ScoreKeywordResponse;
import com.fund.stockProject.score.dto.response.ScoreResponse;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.dto.response.StockWordResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreRepository scoreRepository;
    private final StockRepository stockRepository;
    private final StockKeywordRepository stockKeywordRepository;
    private final ScorePersistenceService scorePersistenceService;
    private final ExecutorService pythonExecutorService;
    private final Semaphore pythonProcessSemaphore;

    private static final Set<Integer> INDEX_STOCK_IDS = Set.of(16492, 16493, 16494, 16495, 16496, 16497);

    @Transactional(readOnly = true)
    public List<Score> findScoresByDate(LocalDate yesterday, LocalDate today) {
        return scoreRepository.findScoresWithoutTodayData(yesterday, today);
    }

    public ScoreResponse getScoreById(Integer id, COUNTRY country) {
        LocalDate today = LocalDate.now();
        // 1) 오늘 날짜 점수 우선 조회
        return scoreRepository.findByStockIdAndDate(id, today)
            .or(() -> scoreRepository.findTopByStockIdOrderByDateDesc(id)) // 2) 없으면 가장 최근 점수
            .map(score -> {
                int scoreValue = (country == COUNTRY.KOREA) ? score.getScoreKorea() : score.getScoreOversea();
                return ScoreResponse.builder().score(scoreValue).build();
            })
            .orElseThrow(() -> new RuntimeException("Score not found for stock: " + id));
    }


    public List<StockWordResponse> getWordCloud(final String symbol, final COUNTRY country) {
        try {
            log.info("Starting word cloud generation - symbol: {}, country: {}", symbol, country);
            String scriptPath = "/app/scripts/wc.py";

            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            pythonProcessSemaphore.acquire();
            Process process = processBuilder.start();

            Future<String> outputFuture = pythonExecutorService.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines()
                    .filter(line -> line.trim().startsWith("{") && line.trim().endsWith("}"))
                    .collect(Collectors.joining("\n"));
            });

            String output;
            try {
                output = outputFuture.get(65, TimeUnit.SECONDS);
            } catch (Exception ex) {
                process.destroyForcibly();
                log.error("Word cloud Python script timed out - symbol: {}, country: {}", symbol, country, ex);
                throw new RuntimeException("Python script timed out", ex);
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("Word cloud Python process did not terminate in time - symbol: {}, country: {}", symbol, country);
                throw new RuntimeException("Python process did not terminate in time");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Word cloud Python script execution failed - symbol: {}, country: {}, exitCode: {}", symbol, country, exitCode);
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

            log.info("Word cloud generation completed successfully - symbol: {}, country: {}, wordCount: {}", symbol, country, wordCloud.size());
            return wordCloud;

        } catch (Exception e) {
            log.error("Failed to execute word cloud Python script - symbol: {}, country: {}", symbol, country, e);
            throw new RuntimeException("Failed to execute Python script", e);
        } finally {
            if (pythonProcessSemaphore.availablePermits() < 2) {
                pythonProcessSemaphore.release();
            }
        }
    }

    public ScoreIndexResponse getIndexScore() {
        // 심볼 리스트 정의
        List<String> symbols = List.of(
            "KOSPI_INDEX", "KOSDAQ_INDEX", "SNP500_INDEX", "NASDAQ_INDEX", "KOSPI_VIX", "SNP500_VIX"
        );

        // 오늘과 어제 날짜 계산
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 심볼별 점수 맵 초기화
        Integer kospiIndex = null, kospiIndexDiff = null;
        Integer kosdaqIndex = null, kosdaqIndexDiff = null;
        Integer snpIndex = null, snpIndexDiff = null;
        Integer nasdaqIndex = null, nasdaqIndexDiff = null;
        Integer kospiVix = null, kospiVixDiff = null;
        Integer snpVix = null, snpVixDiff = null;

        for (String symbol : symbols) {
            // Stock 객체 가져오기
            Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new RuntimeException("Stock not found for symbol: " + symbol));

            // 오늘 날짜 점수 조회
            Score score = scoreRepository.findByStockIdAndDate(stock.getId(), today)
                .orElseGet(() -> {
                    // 오늘 데이터가 없으면 어제 날짜 데이터 조회
                    return scoreRepository.findByStockIdAndDate(stock.getId(), yesterday)
                        .orElseThrow(() -> new RuntimeException(
                            "Score not found for stock: " + stock.getId()));
                });

            // 각 심볼에 따라 점수 할당
            switch (symbol) {
                case "KOSPI_INDEX":
                    kospiIndex = score.getScoreKorea();
                    kospiIndexDiff = score.getDiff();
                    break;
                case "KOSDAQ_INDEX":
                    kosdaqIndex = score.getScoreKorea();
                    kosdaqIndexDiff = score.getDiff();
                    break;
                case "SNP500_INDEX":
                    snpIndex = score.getScoreKorea();
                    snpIndexDiff = score.getDiff();
                    break;
                case "NASDAQ_INDEX":
                    nasdaqIndex = score.getScoreKorea();
                    nasdaqIndexDiff = score.getDiff();
                    break;
                case "KOSPI_VIX":
                    kospiVix = score.getScoreKorea();
                    kospiVixDiff = score.getDiff();
                    break;
                case "SNP500_VIX":
                    snpVix = score.getScoreKorea();
                    snpVixDiff = score.getDiff();
                    break;
            }
        }

        // ScoreIndexResponse 생성 및 반환
        return new ScoreIndexResponse(
            kospiVix, kospiVixDiff,
            kospiIndex, kospiIndexDiff,
            kosdaqIndex, kosdaqIndexDiff,
            snpVix, snpVixDiff,
            snpIndex, snpIndexDiff,
            nasdaqIndex, nasdaqIndexDiff
        );
    }

    // 점수 & 키워드 업데이트
    public void updateScoreAndKeyword(Integer id, COUNTRY country, int yesterdayScore) {

        // 인덱스 종목은 업데이트 하지 않음
        if(INDEX_STOCK_IDS.contains(id)) {
            return;
        }

        try {
            Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Could not find stock"));
            // STEP1: AI 결과 가져오기
            ScoreKeywordResponse scoreKeywordResponse = executeUpdateAI(stock.getSymbol(), country);
            scorePersistenceService.saveScoreAndKeyword(stock.getId(), country, yesterdayScore,
                scoreKeywordResponse);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update score and keyword", e);
        }
    }

    // 지수 업데이트 스케줄러 로직
    public void updateIndexScore() {
        try {
            // STEP 1: Python 스크립트 실행하여 결과 가져오기
            Map<String, Integer> indexScores = executeStockIndexUpdateAI();

            // STEP 2: 심볼과 결과값 매핑
            Map<String, String> symbolMapping = Map.of(
                "kospi", "KOSPI_INDEX",
                "kosdaq", "KOSDAQ_INDEX",
                "snp500", "SNP500_INDEX",
                "nasdaq", "NASDAQ_INDEX",
                "vixKospi", "KOSPI_VIX",
                "vixSnp", "SNP500_VIX"
            );

            // STEP 3: 매핑된 데이터로 점수 업데이트
            for (Map.Entry<String, String> entry : symbolMapping.entrySet()) {
                String resultKey = entry.getKey();
                String stockSymbol = entry.getValue();

                // Stock 찾기
                Stock stock = stockRepository.findBySymbol(stockSymbol)
                    .orElseThrow(
                        () -> new RuntimeException("Stock not found for symbol: " + stockSymbol));

                // 어제 날짜 점수 조회 (없으면 기본값 0)
                LocalDate yesterday = LocalDate.now().minusDays(1);
                int yesterdayScore = scoreRepository.findByStockIdAndDate(stock.getId(), yesterday)
                    .map(Score::getScoreKorea)
                    .orElse(0);

                // 오늘 점수 가져오기
                int finalScore = indexScores.getOrDefault(resultKey, 0);

                // 점수 저장
                Score newScore = Score.builder()
                    .stockId(stock.getId())
                    .date(LocalDate.now())
                    .scoreKorea(finalScore)
                    .scoreNaver(finalScore)
                    .scoreReddit(9999)
                    .scoreOversea(finalScore)
                    .diff(finalScore - yesterdayScore)
                    .build();
                newScore.setStock(stock);
                scoreRepository.save(newScore);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to update index scores", e);
        }
    }

    private Map<String, Integer> executeStockIndexUpdateAI() {
        try {
            String scriptPath = "/app/scripts/stockindex.py"; // 스크립트 경로
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Python 출력 읽기
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                    "Python script execution failed with exit code: " + exitCode + "\nOutput: "
                        + output);
            }

            // JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Integer> result = objectMapper.readValue(output, new TypeReference<>() {
            });
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Python script", e);
        }
    }

    private int executeScoreAI(String symbol, COUNTRY country) {
        try {
            log.info("Starting score AI execution - symbol: {}, country: {}", symbol, country);
            // Python 스크립트 경로
            String scriptPath = "/app/scripts/score.py";

            // ProcessBuilder를 사용하여 Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            pythonProcessSemaphore.acquire();
            Process process = processBuilder.start();

            Future<String> outputFuture = pythonExecutorService.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines()
                    .filter(line -> line.trim().startsWith("{") && line.trim().endsWith("}"))
                    .collect(Collectors.joining("\n"));
            });

            String output;
            try {
                output = outputFuture.get(65, TimeUnit.SECONDS);
            } catch (Exception ex) {
                process.destroyForcibly();
                log.error("Score AI Python script timed out - symbol: {}, country: {}", symbol, country, ex);
                throw new RuntimeException("Python script timed out", ex);
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("Score AI Python process did not terminate in time - symbol: {}, country: {}", symbol, country);
                throw new RuntimeException("Python process did not terminate in time");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Score AI Python script execution failed - symbol: {}, country: {}, exitCode: {}", symbol, country, exitCode);
                throw new RuntimeException("Python script execution failed with exit code: " + exitCode);
            }

            // Python 스크립트에서 출력된 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(output);
            int finalScore = jsonNode.get("final_score").asInt();
            log.info("Score AI execution completed successfully - symbol: {}, country: {}, score: {}", symbol, country, finalScore);
            return finalScore;

        } catch (Exception e) {
            log.error("Failed to execute score AI Python script - symbol: {}, country: {}", symbol, country, e);
            throw new RuntimeException("Failed to execute Python script", e);
        } finally {
            if (pythonProcessSemaphore.availablePermits() < 2) {
                pythonProcessSemaphore.release();
            }
        }
    }

    private ScoreKeywordResponse executeUpdateAI(String symbol, COUNTRY country) {
        try {
            log.info("Starting update AI execution - symbol: {}, country: {}", symbol, country);
            // Python 스크립트 경로
            String scriptPath = "/app/scripts/update.py";

            // ProcessBuilder를 사용하여 Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            pythonProcessSemaphore.acquire();
            Process process = processBuilder.start();

            Future<String> outputFuture = pythonExecutorService.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return reader.lines().collect(Collectors.joining("\n"));
            });

            String output;
            try {
                output = outputFuture.get(65, TimeUnit.SECONDS);
            } catch (Exception ex) {
                process.destroyForcibly();
                log.error("Update AI Python script timed out - symbol: {}, country: {}", symbol, country, ex);
                throw new RuntimeException("Python script timed out", ex);
            }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("Update AI Python process did not terminate in time - symbol: {}, country: {}", symbol, country);
                throw new RuntimeException("Python process did not terminate in time");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Update AI Python script execution failed - symbol: {}, country: {}, exitCode: {}", symbol, country, exitCode);
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

            log.info("Update AI execution completed successfully - symbol: {}, country: {}, score: {}, keywordCount: {}", symbol, country, finalScore, topKeywords.size());
            return new ScoreKeywordResponse(finalScore, topKeywords);

        } catch (Exception e) {
            log.error("Failed to execute update AI Python script - symbol: {}, country: {}", symbol, country, e);
            throw new RuntimeException("Failed to execute Python script", e);
        } finally {
            if (pythonProcessSemaphore.availablePermits() < 2) {
                pythonProcessSemaphore.release();
            }
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
