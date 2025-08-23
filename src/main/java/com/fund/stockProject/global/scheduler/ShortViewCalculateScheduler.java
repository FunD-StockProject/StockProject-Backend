package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.shortview.entity.StockFeatureVector;
import com.fund.stockProject.shortview.entity.StockSimilarity;
import com.fund.stockProject.shortview.repository.StockFeatureVectorRepository;
import com.fund.stockProject.shortview.repository.StockSimilarityRepository;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 🚀 추천 시스템 데이터 생성 스케줄러 (성능 개선 버전)
 *
 * ✅ 주요 개선 사항:
 * - O(n^2) 유사도 계산 로직에 병렬 스트림(parallelStream)을 적용하여 실행 시간 대폭 단축.
 * - 각 로직을 명확한 책임에 따라 별도의 메서드로 분리하여 가독성 및 유지보수성 향상.
 * - Jaccard 유사도 계산 로직 최적화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortViewCalculateScheduler {

    private final StockRepository stockRepository;
    private final ScoreRepository scoreRepository;
    private final PreferenceRepository preferenceRepository;
    private final StockFeatureVectorRepository stockFeatureVectorRepository;
    private final StockSimilarityRepository stockSimilarityRepository;

    private static final double SIMILARITY_THRESHOLD = 0.5; // 패턴 유사도 저장 최소 임계값
    private static final int VOLATILITY_WINDOW = 7; // 변동성 계산 기간

    // 가중치 상수 정의
    private static final double SECTOR_SIMILARITY_WEIGHT = 0.3;
    private static final double VOLATILITY_WEIGHT = 0.2;
    private static final double TREND_WEIGHT = 0.3;
    private static final double SCORE_WEIGHT = 0.2;

    /**
     * 매일 새벽 4시에 추천 시스템에 필요한 데이터를 미리 계산하여 저장합니다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void createRecommendationData() {
        log.info("🚀 추천 데이터 생성 배치 작업을 시작합니다.");

        long startTime = System.currentTimeMillis();

        // Step 1: 모든 주식의 특징 벡터 계산 및 저장
        List<StockFeatureVector> featureVectors = calculateAndSaveAllStockFeatureVectors();
        log.info("✅ [Step 1/3] {}개의 주식 특징 벡터를 생성하고 저장했습니다.", featureVectors.size());

        // Step 2: 특징 벡터 기반 '점수 패턴' 유사도 계산 및 저장 (병렬 처리 적용)
        int patternSimilarities = calculateAndSavePatternSimilarity(featureVectors);
        log.info("✅ [Step 2/3] {}개의 점수 패턴 유사도 데이터를 저장했습니다.", patternSimilarities);

        // Step 3: 사용자 행동 기반 '협업 필터링' 유사도 계산 및 저장
        int cfSimilarities = calculateAndSaveCollaborativeFilteringSimilarity();
        log.info("✅ [Step 3/3] {}개의 협업 필터링 유사도 데이터를 저장했습니다.", cfSimilarities);

        long endTime = System.currentTimeMillis();
        log.info("🎉 추천 데이터 생성 배치 작업을 완료했습니다. (총 소요 시간: {}ms)", (endTime - startTime));
    }

    /**
     * 모든 주식의 점수 특징(평균, 추세, 변동성 등)을 계산하고 DB에 저장합니다.
     */
    private List<StockFeatureVector> calculateAndSaveAllStockFeatureVectors() {
        List<Stock> allStocks = stockRepository.findAll();
        // [수정됨] .filter().map() 체인을 flatMap(Optional::stream)으로 변경하여 타입 추론 오류 해결 및 코드 간소화
        List<StockFeatureVector> vectors = allStocks.parallelStream() // 병렬 처리로 성능 향상
                .map(this::createFeatureVectorForStock)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        stockFeatureVectorRepository.deleteAllInBatch();
        return stockFeatureVectorRepository.saveAll(vectors);
    }

    private Optional<StockFeatureVector> createFeatureVectorForStock(Stock stock) {
        List<Score> recentScores = scoreRepository.findFirst30ByStockIdOrderByDateDesc(stock.getId());
        if (recentScores.size() < 10) return Optional.empty();

        Collections.reverse(recentScores); // 시간 순으로 정렬

        List<Double> koreaScores = recentScores.stream().map(s -> (double) s.getScoreKorea()).toList();
        List<Double> overseaScores = recentScores.stream().map(s -> (double) s.getScoreOversea()).toList();

        StockFeatureVector vector = StockFeatureVector.builder()
                .stock(stock)
                .avgKoreaScore(calculateAverage(koreaScores))
                .avgOverseaScore(calculateAverage(overseaScores))
                .trendKorea(calculateTrend(koreaScores))
                .trendOversea(calculateTrend(overseaScores))
                .volatilityKorea(calculateVolatility(koreaScores))
                .volatilityOversea(calculateVolatility(overseaScores))
                .momentumKorea(calculateMomentum(koreaScores))
                .momentumOversea(calculateMomentum(overseaScores))
                .consistencyKorea(calculateConsistency(koreaScores))
                .consistencyOversea(calculateConsistency(overseaScores))
                .build();
        vector.setSector(stock.getSector());
        return Optional.of(vector);
    }

    /**
     * 특징 벡터들 간의 코사인 유사도를 계산하여 '점수 패턴' 유사도를 저장합니다.
     */
    private int calculateAndSavePatternSimilarity(List<StockFeatureVector> vectors) {
        // 병렬 스트림으로 처리하기 위해 스레드 안전한 리스트 사용
        List<StockSimilarity> similarities = Collections.synchronizedList(new ArrayList<>());

        // IntStream을 사용한 병렬 처리
        IntStream.range(0, vectors.size()).parallel().forEach(i -> {
            for (int j = i + 1; j < vectors.size(); j++) {
                StockFeatureVector v1 = vectors.get(i);
                StockFeatureVector v2 = vectors.get(j);

                double similarity = calculateVectorSimilarity(v1, v2);

                if (similarity > SIMILARITY_THRESHOLD) {
                    // 양방향으로 저장하여 조회 용이성 확보
                    similarities.add(new StockSimilarity(v1.getStockId(), v2.getStockId(), "PATTERN", similarity));
                    similarities.add(new StockSimilarity(v2.getStockId(), v1.getStockId(), "PATTERN", similarity));
                }
            }
        });

        // deleteAllByType은 Repository에 @Modifying @Query("DELETE FROM StockSimilarity s WHERE s.type = :type") 로 구현 필요
        stockSimilarityRepository.deleteAllByType("PATTERN");
        stockSimilarityRepository.saveAll(similarities);
        return similarities.size();
    }

    /**
     * 두 특징 벡터의 유사도를 계산합니다.
     */
    private double calculateVectorSimilarity(StockFeatureVector v1, StockFeatureVector v2) {
        double[] vec1 = buildNormalizedVector(v1, v2);
        double[] vec2 = buildNormalizedVector(v2, v1);
        return calculateCosineSimilarity(vec1, vec2);
    }

    /**
     * 정규화된 특징 배열을 생성합니다.
     */
    private double[] buildNormalizedVector(StockFeatureVector target, StockFeatureVector other) {
        double sectorSimilarity = (target.getSector() == other.getSector()) ? 1.0 : 0.0;
        return new double[]{
                normalizeScore(target.getAvgKoreaScore()) * SCORE_WEIGHT,
                normalizeScore(target.getAvgOverseaScore()) * SCORE_WEIGHT,
                normalizeTrend(target.getTrendKorea()) * TREND_WEIGHT,
                normalizeTrend(target.getTrendOversea()) * TREND_WEIGHT,
                normalizeVolatility(target.getVolatilityKorea()) * VOLATILITY_WEIGHT,
                normalizeVolatility(target.getVolatilityOversea()) * VOLATILITY_WEIGHT,
                sectorSimilarity * SECTOR_SIMILARITY_WEIGHT
        };
    }

    /**
     * 사용자의 북마크 기록을 기반으로 Jaccard 유사도를 계산하여 '협업 필터링' 유사도를 저장합니다.
     */
    private int calculateAndSaveCollaborativeFilteringSimilarity() {
        List<Preference> bookmarks = preferenceRepository.findAllByPreferenceType(PreferenceType.BOOKMARK);
        Map<Integer, Set<Integer>> stockToUsersMap = bookmarks.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStock().getId(),
                        Collectors.mapping(p -> p.getUser().getId(), Collectors.toSet())
                ));

        List<Integer> stockIds = new ArrayList<>(stockToUsersMap.keySet());
        List<StockSimilarity> similarities = Collections.synchronizedList(new ArrayList<>());

        // IntStream을 사용한 병렬 처리
        IntStream.range(0, stockIds.size()).parallel().forEach(i -> {
            for (int j = i + 1; j < stockIds.size(); j++) {
                Integer stock1 = stockIds.get(i);
                Integer stock2 = stockIds.get(j);

                Set<Integer> users1 = stockToUsersMap.get(stock1);
                Set<Integer> users2 = stockToUsersMap.get(stock2);

                long intersection = users1.stream().filter(users2::contains).count();
                if (intersection > 0) {
                    long union = users1.size() + users2.size() - intersection;
                    double jaccardSimilarity = (double) intersection / union;
                    similarities.add(new StockSimilarity(stock1, stock2, "CF", jaccardSimilarity));
                    similarities.add(new StockSimilarity(stock2, stock1, "CF", jaccardSimilarity));
                }
            }
        });

        stockSimilarityRepository.deleteAllByType("CF");
        stockSimilarityRepository.saveAll(similarities);
        return similarities.size();
    }

    // --- Helper & Calculation Methods ---

    private double calculateAverage(List<Double> scores) {
        return scores.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private double calculateTrend(List<Double> data) {
        int n = data.size();
        if (n < 2) return 0.0;
        double[] x = IntStream.range(0, n).mapToDouble(i -> i).toArray();
        double sumX = Arrays.stream(x).sum();
        double sumY = data.stream().mapToDouble(d -> d).sum();
        double sumXY = IntStream.range(0, n).mapToDouble(i -> x[i] * data.get(i)).sum();
        double sumX2 = Arrays.stream(x).map(val -> val * val).sum();
        double denominator = n * sumX2 - sumX * sumX;
        return denominator == 0 ? 0 : (n * sumXY - sumX * sumY) / denominator;
    }

    private double calculateVolatility(List<Double> data) {
        if (data.size() < VOLATILITY_WINDOW) return 0.0;
        List<Double> recentData = data.subList(data.size() - VOLATILITY_WINDOW, data.size());
        double mean = recentData.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = recentData.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }

    private double calculateMomentum(List<Double> data) {
        if (data.size() < 5) return 0.0;
        double recent = data.get(data.size() - 1);
        double past = data.get(data.size() - 5);
        return past == 0 ? 0 : (recent - past) / past;
    }

    private double calculateConsistency(List<Double> data) {
        if (data.size() < 2) return 1.0;
        double avgChange = IntStream.range(1, data.size())
                .mapToDouble(i -> Math.abs(data.get(i) - data.get(i-1)))
                .average().orElse(0.0);
        return avgChange == 0 ? 1.0 : Math.max(0, 1.0 - avgChange / 10.0);
    }

    private double calculateCosineSimilarity(double[] vecA, double[] vecB) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    // --- Normalization Methods ---
    private double normalizeScore(double score) { return Math.min(1.0, Math.max(0.0, score / 100.0)); }
    private double normalizeTrend(double trend) { return Math.min(1.0, Math.max(-1.0, trend / 5.0)); }
    private double normalizeVolatility(double volatility) { return Math.min(1.0, volatility / 20.0); }
}
