package com.fund.stockProject.score.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.keyword.dto.KeywordDto;
import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
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
            Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Could not find stock"));
            // STEP1: AI 실행
            int finalScore = executeScoreAI(stock.getSymbol(), country);

            // STEP2: SCORE 데이터 저장
            Score newScore = Score.builder()
                .stockId(stock.getId())
                .date(LocalDate.now())
                .scoreKorea(country == COUNTRY.KOREA ? finalScore : 9999)
                .scoreNaver(finalScore)
                .scoreReddit(9999)
                .scoreOversea(country == COUNTRY.OVERSEA ? finalScore : 9999)
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
        } else if (isDateExist(id)) {
            Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Could not find stock"));
            // STEP1: AI 실행
            int finalScore = executeScoreAI(stock.getSymbol(), country);

            // STEP2: SCORE 데이터 저장
            Score newScore = Score.builder()
                .stockId(stock.getId())
                .date(LocalDate.now())
                .scoreKorea(country == COUNTRY.KOREA ? finalScore : 9999)
                .scoreNaver(finalScore)
                .scoreReddit(9999)
                .scoreOversea(country == COUNTRY.OVERSEA ? finalScore : 9999)
                .diff(0)
                .build();
            // `stock` 연관 설정
            newScore.setStock(stock);
            scoreRepository.save(newScore);

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
                        .orElseThrow(() -> new RuntimeException(
                            "Score not found for stock_id: " + id + " and date: " + today + " or "
                                + yesterday));
                });

            // country에 따라 점수 선택
            int scoreValue =
                (country == COUNTRY.KOREA) ? score.getScoreKorea() : score.getScoreOversea();

            return ScoreResponse.builder()
                .score(scoreValue)
                .build();
        }
    }

    private boolean isDateExist(Integer id) {
        LocalDate today = LocalDate.now();

        Score score = scoreRepository.findByStockIdAndDate(id, today)
            .orElseGet(
                () -> scoreRepository.findByStockIdAndDate(id, today.minusDays(1)).orElse(null));

        return score == null;
    }


    public List<StockWordResponse> getWordCloud(final String symbol, final COUNTRY country) {
        try {
            String scriptPath = "wc.py";

            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Asynchronously read the script output
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                return reader.lines()
                    .filter(line -> line.trim().startsWith("{") && line.trim()
                        .endsWith("}")) // JSON format filtering
                    .collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // Maximum 60 seconds wait

            // Check the process exit code
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                    "Python script execution failed with exit code: " + exitCode);
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
    @Transactional
    public void updateScoreAndKeyword(Integer id, COUNTRY country, int yesterdayScore) {
        Stock stock = stockRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Could not find stock"));
        try {
            // STEP1: AI 결과 가져오기
            ScoreKeywordResponse scoreKeywordResponse = executeUpdateAI(stock.getSymbol(), country);

            // STEP2: SCORE 데이터 저장
            int finalScore = scoreKeywordResponse.getFinalScore();
            Score newScore = Score.builder()
                .stockId(stock.getId())
                .date(LocalDate.now())
                .scoreKorea(country == COUNTRY.KOREA ? finalScore : 9999)
                .scoreNaver(finalScore)
                .scoreReddit(9999)
                .scoreOversea(country == COUNTRY.OVERSEA ? finalScore : 9999)
                .diff(finalScore - yesterdayScore)
                .build();

            // `stock` 연관 설정
            newScore.setStock(stock);
            scoreRepository.save(newScore);

            // 기존 StockKeyword 삭제
            stockKeywordRepository.deleteByStock(stock);
            scoreKeywordResponse.getTopKeywords().forEach(keywordDto -> {
                Keyword newKeyword = Keyword.builder()
                    .name(keywordDto.getWord())
                    .frequency(keywordDto.getFreq())
                    .build();

                keywordRepository.save(newKeyword);

                // StockKeyword 테이블에 매핑 정보 저장
                StockKeyword stockKeyword = StockKeyword.builder()
                    .stock(stock)
                    .keyword(newKeyword)
                    .build();
                stockKeywordRepository.save(stockKeyword);
            });
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

    private boolean isFirst(Integer id) {
        // 1111-11-11 날짜의 데이터가 존재하면 첫 인간지표로 판단
        return scoreRepository.existsByStockIdAndDate(id, LocalDate.of(1111, 11, 11));
    }

    private Map<String, Integer> executeStockIndexUpdateAI() {
        try {
            String scriptPath = "stockindex.py"; // 스크립트 경로
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
            // Python 스크립트 경로
            String scriptPath = "score.py";

            // ProcessBuilder를 사용하여 Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 비동기 스트림 읽기
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                return reader.lines()
                    .filter(line -> line.trim().startsWith("{") && line.trim()
                        .endsWith("}")) // JSON 형식 필터링
                    .collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // 최대 120초 대기

            // 프로세스 종료 코드 확인
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                    "Python script execution failed with exit code: " + exitCode);
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
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol,
                country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 비동기 스트림 읽기
            String output = Executors.newSingleThreadExecutor().submit(() -> {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                return reader.lines().collect(Collectors.joining("\n"));
            }).get(60, TimeUnit.SECONDS); // 최대 60초 대기

            // 프로세스 종료 코드 확인
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                    "Python script execution failed with exit code: " + exitCode);
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
