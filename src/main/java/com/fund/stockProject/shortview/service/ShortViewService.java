package com.fund.stockProject.shortview.service;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.shortview.dto.ShortViewResponse;
import com.fund.stockProject.shortview.entity.StockFeatureVector;
import com.fund.stockProject.shortview.entity.StockSimilarity;
import com.fund.stockProject.shortview.repository.StockFeatureVectorRepository;
import com.fund.stockProject.shortview.repository.StockSimilarityRepository;
import com.fund.stockProject.stock.domain.SECTOR;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 🚀 숏뷰 주식 추천 서비스 (상세 주석 버전)
 *
 * 이 서비스는 사용자에게 개인화된 주식을 추천하는 핵심 비즈니스 로직을 담당합니다.
 * 하이브리드 추천 시스템을 사용하여 여러 추천 기법의 결과를 조합하여 최종 추천을 생성합니다.
 *
 * ✅ 주요 기능:
 * - 사용자의 최근 활동(북마크)을 기반으로 개인화된 주식 추천
 * - 신규 사용자(Cold Start)를 위한 인기 주식 추천
 * - N+1 문제 해결 및 성능 최적화를 위한 2단계 추천 로직 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortViewService {

    // --- 의존성 주입 (필요한 Repository 및 Service) ---
    private final PreferenceRepository preferenceRepository;
    private final StockSimilarityRepository stockSimilarityRepository;
    private final StockRepository stockRepository;
    private final StockFeatureVectorRepository stockFeatureVectorRepository;
    private final SecurityService securityService;

    // --- 추천 알고리즘 가중치 상수 ---
    // 각 추천 요소의 중요도를 나타내며, 모두 합하면 1.0이 됩니다.
    private static final double CF_WEIGHT = 0.30;           // 협업 필터링 (나와 비슷한 사용자가 선호하는 주식)
    private static final double CONTENT_WEIGHT = 0.25;      // 콘텐츠 기반 (내가 선호하는 주식과 패턴이 비슷한 주식)
    private static final double TREND_WEIGHT = 0.20;        // 트렌드 (최근 점수 추세가 좋은 주식)
    private static final double DIVERSITY_WEIGHT = 0.15;    // 다양성 (포트폴리오 다각화를 위한 다른 섹터의 주식)
    private static final double FRESHNESS_WEIGHT = 0.10;    // 신선도 (최근 모멘텀이 좋은 주식)
    private static final int CANDIDATE_LIMIT_PER_BOOKMARK = 20; // 북마크 1개당 생성할 추천 후보 주식 수

    /**
     * 사용자에게 추천할 주식 엔티티를 반환하는 메인 메서드입니다.
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티
     */
    public Stock getRecommendedStock(User user) {
        // Step 1: 사용자의 최근 북마크 5개 조회
        List<Preference> recentBookmarks = preferenceRepository
                .findFirst5ByUserAndPreferenceTypeOrderByCreatedAtDesc(user, PreferenceType.BOOKMARK);

        // Step 2: Cold Start 처리
        // 북마크 기록이 없는 신규 사용자의 경우, 개인화 추천이 불가능하므로 인기 주식을 추천합니다.
        if (recentBookmarks.isEmpty()) {
            log.info("사용자(id:{})의 북마크 기록이 없어 인기 주식을 추천합니다.", user.getId());
            return getPopularStock(user.getId());
        }

        // Step 3: 추천에서 제외할 주식 목록 조회 (이미 북마크했거나, 다시 보지 않기로 설정한 주식)
        Set<Integer> excludedStockIds = getExcludedStockIds(user.getId(), recentBookmarks);

        // Step 4: 핵심 로직 - 하이브리드 추천 점수 계산
        Map<Integer, RecommendationScore> candidateScores = calculateAdvancedCandidateScores(recentBookmarks, excludedStockIds);

        // Step 5: 계산된 점수가 가장 높은 주식 선택
        Optional<Map.Entry<Integer, RecommendationScore>> bestCandidate = candidateScores.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        // Step 6: 최종 추천 주식 결정
        // 만약 적절한 추천 후보군이 없다면, 인기 주식을 대신 추천합니다 (Fallback 로직).
        if (bestCandidate.isEmpty()) {
            log.info("후보군 필터링 후 추천할 주식이 없어 인기 주식을 추천합니다. (user_id:{})", user.getId());
            return getPopularStock(user.getId());
        }

        Integer recommendedStockId = bestCandidate.get().getKey();
        RecommendationScore score = bestCandidate.get().getValue();
        log.info("사용자(id:{})에게 주식(id:{})을 개선된 알고리즘으로 추천했습니다. " +
                        "총점: {:.3f}, CF: {:.3f}, Content: {:.3f}, Trend: {:.3f}, Diversity: {:.3f}, Freshness: {:.3f}",
                user.getId(), recommendedStockId,
                score.getTotalScore(), score.collaborativeScore, score.contentScore,
                score.trendScore, score.diversityScore, score.freshnessScore);

        return stockRepository.findById(recommendedStockId)
                .orElseThrow(() -> new RuntimeException("추천된 주식을 찾을 수 없습니다. stock_id: " + recommendedStockId));
    }

    /**
     * 하이브리드 추천 점수를 계산하는 핵심 메서드입니다. (2단계 추천 방식 적용)
     * 1단계: 후보군 생성 (Candidate Generation) - 추천 가능성이 높은 주식들을 소수 선별합니다.
     * 2단계: 점수 계산 (Scoring & Ranking) - 선별된 후보군에 대해서만 복잡한 점수를 계산하여 순위를 매깁니다.
     */
    private Map<Integer, RecommendationScore> calculateAdvancedCandidateScores(List<Preference> bookmarks, Set<Integer> excludedStockIds) {
        // --- 1단계: 후보군 생성 ---

        // 사용자가 북마크한 주식들의 ID 목록을 만듭니다.
        List<Integer> bookmarkedStockIds = bookmarks.stream()
                .map(p -> p.getStock().getId())
                .collect(Collectors.toList());

        // 스케줄러가 미리 계산해 둔 유사도 테이블을 조회합니다.
        // IN 쿼리를 사용하여 북마크한 모든 주식과 관련된 유사도를 한 번의 쿼리로 가져와 N+1 문제를 해결합니다.
        List<StockSimilarity> cfSimilarities = stockSimilarityRepository
                .findByStockId1InAndType(bookmarkedStockIds, "CF", PageRequest.of(0, CANDIDATE_LIMIT_PER_BOOKMARK * bookmarks.size()));
        List<StockSimilarity> patternSimilarities = stockSimilarityRepository
                .findByStockId1InAndType(bookmarkedStockIds, "PATTERN", PageRequest.of(0, CANDIDATE_LIMIT_PER_BOOKMARK * bookmarks.size()));

        // 조회된 유사도 목록을 바탕으로 추천 후보 주식 ID 목록(Set)을 생성합니다.
        // 중복을 제거하고, 이미 제외 목록에 있는 주식은 필터링합니다.
        Set<Integer> candidateStockIds = Stream.concat(cfSimilarities.stream(), patternSimilarities.stream())
                .flatMap(sim -> Stream.of(sim.getStockId1(), sim.getStockId2()))
                .filter(id -> !excludedStockIds.contains(id))
                .collect(Collectors.toSet());

        if (candidateStockIds.isEmpty()) {
            return Collections.emptyMap(); // 후보군이 없으면 빈 Map 반환
        }

        // --- 2단계: 점수 계산 ---

        // 후보 주식들에 대한 특징 벡터(미리 계산된 점수 통계)를 한 번의 쿼리로 가져옵니다.
        Map<Integer, StockFeatureVector> featureVectorMap = stockFeatureVectorRepository.findByStockIdIn(candidateStockIds).stream()
                .collect(Collectors.toMap(StockFeatureVector::getStockId, Function.identity()));

        // 최근 북마크일수록 높은 가중치를 부여하기 위해 시간 가중치를 계산합니다.
        Map<Integer, Double> timeWeightMap = bookmarks.stream()
                .collect(Collectors.toMap(p -> p.getStock().getId(), p -> calculateTimeWeight(p.getCreatedAt())));

        // 각 후보 주식에 대해 최종 점수를 계산합니다.
        Map<Integer, RecommendationScore> finalScores = new HashMap<>();
        for (Integer candidateId : candidateStockIds) {
            StockFeatureVector vector = featureVectorMap.get(candidateId);
            if (vector == null) continue; // 특징 벡터가 없는 주식은 건너뜁니다.

            RecommendationScore score = new RecommendationScore();
            // 각 추천 요소별 점수를 계산하고 가중치를 곱합니다.
            score.collaborativeScore = calculateScoreFromSimilarities(candidateId, cfSimilarities, timeWeightMap, bookmarkedStockIds) * CF_WEIGHT;
            score.contentScore = calculateScoreFromSimilarities(candidateId, patternSimilarities, timeWeightMap, bookmarkedStockIds) * CONTENT_WEIGHT;
            score.trendScore = calculateTrendScore(vector) * TREND_WEIGHT;
            score.diversityScore = calculateDiversityScore(vector) * DIVERSITY_WEIGHT;
            score.freshnessScore = calculateFreshnessScore(vector) * FRESHNESS_WEIGHT;

            finalScores.put(candidateId, score);
        }
        return finalScores;
    }

    /**
     * 미리 조회된 유사도 목록을 기반으로 협업 필터링 또는 콘텐츠 기반 점수를 계산합니다.
     */
    private double calculateScoreFromSimilarities(Integer candidateId, List<StockSimilarity> similarities, Map<Integer, Double> timeWeightMap, List<Integer> bookmarkedStockIds) {
        double totalScore = 0.0;
        for (StockSimilarity sim : similarities) {
            Integer bookmarkedId = null;
            // 후보 주식과 북마크된 주식 간의 유사도인지 확인
            if (sim.getStockId1().equals(candidateId) && bookmarkedStockIds.contains(sim.getStockId2())) {
                bookmarkedId = sim.getStockId2();
            } else if (sim.getStockId2().equals(candidateId) && bookmarkedStockIds.contains(sim.getStockId1())) {
                bookmarkedId = sim.getStockId1();
            }

            if (bookmarkedId != null) {
                // 시간 가중치를 적용하여 점수 합산
                double timeWeight = timeWeightMap.getOrDefault(bookmarkedId, 1.0);
                totalScore += sim.getScore() * timeWeight;
            }
        }
        return totalScore;
    }

    /**
     * 특징 벡터를 기반으로 트렌드 점수를 계산합니다. (점수 추세)
     */
    private double calculateTrendScore(StockFeatureVector vector) {
        return Math.abs(vector.getTrendKorea()) + Math.abs(vector.getTrendOversea());
    }

    /**
     * 특징 벡터를 기반으로 다양성 점수를 계산합니다. (다른 섹터 추천)
     */
    private double calculateDiversityScore(StockFeatureVector vector) {
        SECTOR sector = vector.getSector();
        // UNKNOWN 섹터에 약간의 페널티를 부여하여, 명확한 섹터의 주식이 더 추천되도록 유도합니다.
        return sector == SECTOR.UNKNOWN ? 0.5 : 1.0;
    }

    /**
     * 특징 벡터를 기반으로 신선도 점수를 계산합니다. (최근 모멘텀)
     */
    private double calculateFreshnessScore(StockFeatureVector vector) {
        double momentumScore = Math.abs(vector.getMomentumKorea()) + Math.abs(vector.getMomentumOversea());
        // 변동성이 너무 높은 주식은 위험하므로, 변동성이 낮을수록 점수가 높게 나오도록 조정합니다.
        double volatilityScore = 1.0 - Math.min(1.0, (vector.getVolatilityKorea() + vector.getVolatilityOversea()) / 40.0);
        return (momentumScore * 0.7 + volatilityScore * 0.3);
    }

    /**
     * 추천된 주식의 실시간 가격 정보와 함께 DTO를 반환하는 API용 메서드입니다.
     */
    public Mono<ShortViewResponse> getRecommendedStockWithPrice(User user) {
        Stock recommendedStock = getRecommendedStock(user);
        // 외부 API를 통해 비동기적으로 실시간 가격을 조회합니다.
        return securityService.getRealTimeStockPrice(recommendedStock)
                .map(stockInfo -> ShortViewResponse.fromEntityWithPrice(recommendedStock, stockInfo))
                .onErrorResume(error -> { // 가격 조회 실패 시
                    log.warn("실시간 가격 조회 실패, 기본 정보로 응답합니다. stock_id: {}, error: {}", recommendedStock.getId(), error.getMessage());
                    return Mono.just(ShortViewResponse.fromEntity(recommendedStock));
                });
    }

    /**
     * 시간 가중치를 계산합니다. (최근 북마크에 더 높은 가중치 부여)
     * 지수 감소 함수를 사용하여, 시간이 지날수록 가중치가 점차 감소하도록 설계합니다.
     */
    private double calculateTimeWeight(LocalDateTime createdAt) {
        long hoursSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
        return Math.exp(-hoursSinceCreation / 24.0); // 24시간을 반감기로 설정
    }

    /**
     * 추천에서 제외할 주식 ID 목록을 조회합니다.
     */
    private Set<Integer> getExcludedStockIds(Integer userId, List<Preference> bookmarks) {
        // 현재 북마크한 주식들을 제외 목록에 추가
        Set<Integer> excludedIds = bookmarks.stream()
                .map(p -> p.getStock().getId())
                .collect(Collectors.toSet());
        // '다시 보지 않기'로 설정한 주식들을 제외 목록에 추가
        excludedIds.addAll(preferenceRepository.findStockIdsByUserIdAndPreferenceType(userId, PreferenceType.NEVER_SHOW));
        return excludedIds;
    }

    /**
     * Cold Start 또는 비회원을 위한 인기 주식을 조회합니다.
     * @param userId 사용자 ID (비회원일 경우 null)
     */
    public Stock getPopularStock(Integer userId) {
        Set<Integer> excludedIds = (userId != null) ? getExcludedStockIds(userId, Collections.emptyList()) : Collections.emptySet();
        List<StockFeatureVector> featureVectors = stockFeatureVectorRepository.findAll();

        // 여러 특징을 조합하여 가장 '인기 있다'고 판단되는 주식을 찾습니다.
        Optional<StockFeatureVector> bestVector = featureVectors.stream()
                .filter(v -> !excludedIds.contains(v.getStockId()))
                .max(Comparator.comparingDouble(v ->
                        (v.getAvgKoreaScore() + v.getAvgOverseaScore() + v.getConsistencyKorea() + v.getConsistencyOversea()
                                - (v.getVolatilityKorea() + v.getVolatilityOversea()) / 10.0)
                ));

        return bestVector.flatMap(vector -> stockRepository.findById(vector.getStockId()))
                .orElseGet(() -> getFallbackPopularStock(excludedIds)); // 만약 위 로직으로 못찾으면 DB의 점수 순으로 찾음
    }

    /**
     * 인기 주식 조회에 대한 최종 Fallback 로직입니다.
     */
    private Stock getFallbackPopularStock(Set<Integer> excludedIds) {
        // [수정됨] createdAt 정렬이 의미 없다는 지적을 반영하여, 전체 주식 중 무작위로 하나를 선택하도록 수정합니다.
        // 이렇게 하면 폴백 추천 시마다 다른 주식을 보여줄 수 있습니다.
        List<Stock> allStocks = stockRepository.findAll();
        if (allStocks.isEmpty()) {
            return null;
        }

        // 제외할 주식을 필터링합니다.
        List<Stock> availableStocks = allStocks.stream()
                .filter(stock -> !excludedIds.contains(stock.getId()))
                .collect(Collectors.toList());

        if (availableStocks.isEmpty()) {
            // 필터링 후 남은 주식이 없으면, 필터링 전 목록에서라도 하나를 반환합니다.
            return allStocks.get(new Random().nextInt(allStocks.size()));
        }

        // 남은 주식 목록을 무작위로 섞은 후 첫 번째 주식을 반환합니다.
        Collections.shuffle(availableStocks);
        return availableStocks.get(0);
    }

    /**
     * 각 추천 요소별 점수를 담기 위한 내부 클래스입니다.
     * Comparable을 구현하여 총점을 기준으로 쉽게 정렬할 수 있도록 합니다.
     */
    private static class RecommendationScore implements Comparable<RecommendationScore> {
        double collaborativeScore = 0.0;
        double contentScore = 0.0;
        double trendScore = 0.0;
        double diversityScore = 0.0;
        double freshnessScore = 0.0;

        double getTotalScore() {
            return collaborativeScore + contentScore + trendScore + diversityScore + freshnessScore;
        }

        @Override
        public int compareTo(RecommendationScore other) {
            return Double.compare(this.getTotalScore(), other.getTotalScore());
        }
    }
}
