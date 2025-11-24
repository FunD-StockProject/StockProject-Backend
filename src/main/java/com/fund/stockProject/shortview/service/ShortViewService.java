package com.fund.stockProject.shortview.service;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import com.fund.stockProject.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 🚀 숏뷰 주식 추천 서비스
 *
 * 점수 기반 가중치 랜덤 추천 시스템을 사용합니다.
 * - 높은 인간지표 점수일수록 선택 확률 증가
 * - Sector 다양성을 고려한 추천
 * - "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
 * - 이전 추천과의 중복을 완전히 방지합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortViewService {

    private final StockRepository stockRepository;
    private final SecurityService securityService;
    private final PreferenceRepository preferenceRepository;
    private final ScoreRepository scoreRepository;
    
    // 사용자별 최근 본 추천 종목 ID 저장 (메모리 캐시)
    // userId -> Set<stockId> (최근 50개까지 저장)
    private static final Map<Integer, Set<Integer>> recentRecommendations = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_RECOMMENDATIONS = 50; // 최근 50개까지만 저장

    /**
     * 사용자에게 추천할 주식 엔티티를 반환하는 메인 메서드입니다.
     * 점수와 sector 기반 가중치 랜덤 추천을 사용합니다.
     * "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
     * 
     * 성능 최적화: N+1 문제 해결을 위해 배치 조회 사용
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티
     */
    public Stock getRecommendedStock(User user) {
        log.info("사용자(id:{})에게 가중치 기반 주식 추천을 시작합니다.", user.getId());
        
        // 사용자가 "다시 보지 않음"으로 설정한 종목 ID 목록 조회 (성능 최적화: stockId만 직접 조회)
        Set<Integer> hiddenStockIds = new HashSet<>(
                preferenceRepository.findStockIdsByUserIdAndPreferenceType(user.getId(), PreferenceType.NEVER_SHOW)
        );
        
        log.info("사용자(id:{})가 숨긴 종목 개수: {}", user.getId(), hiddenStockIds.size());
        
        LocalDate today = LocalDate.now();
        
        // valid = true인 주식만 조회 (성능 최적화: DB에서 필터링)
        List<Stock> validStocks = stockRepository.findAllValidStocks();
        
        // 숨긴 종목 제외
        List<Stock> candidateStocks = validStocks.stream()
                .filter(stock -> !hiddenStockIds.contains(stock.getId()))
                .collect(Collectors.toList());
        
        if (candidateStocks.isEmpty()) {
            log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. (valid=true인 종목 없음)", user.getId());
            return null;
        }
        
        // 배치로 점수 조회 (N+1 문제 해결)
        List<Integer> candidateStockIds = candidateStocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());
        
        // 오늘 날짜 점수와 최신 점수를 배치로 조회
        List<Score> todayScores = scoreRepository.findTodayScoresByStockIds(candidateStockIds, today);
        List<Score> latestScores = scoreRepository.findLatestScoresByStockIds(candidateStockIds);
        
        // stockId -> Score 맵 생성 (오늘 점수 우선, 없으면 최신 점수)
        Map<Integer, Score> scoreMap = new HashMap<>();
        todayScores.forEach(score -> scoreMap.put(score.getStockId(), score));
        latestScores.forEach(score -> scoreMap.putIfAbsent(score.getStockId(), score));
        
        // 점수가 있는 주식만 필터링
        List<Stock> stocksWithScore = candidateStocks.stream()
                .filter(stock -> scoreMap.containsKey(stock.getId()))
                .collect(Collectors.toList());
        
        if (stocksWithScore.isEmpty()) {
            log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. (valid=true이고 점수가 있는 종목 없음)", user.getId());
            return null;
        }
        
        log.info("추천 대상 주식 개수: {}개 (valid=true, 점수 있음)", stocksWithScore.size());
        
        // 각 주식의 가중치 계산 (점수 맵을 전달하여 메모리에서 조회)
        List<StockWithWeight> stocksWithWeight = calculateWeights(stocksWithScore, scoreMap);
        
        // 가중치 기반 랜덤 선택
        Random random = new Random(System.currentTimeMillis() + user.getId());
        Stock recommendedStock = selectWeightedRandom(stocksWithWeight, random);
        
        log.info("사용자(id:{})에게 주식(id:{}, symbol:{}, sector:{}) 가중치 기반 추천 완료", 
                user.getId(), recommendedStock.getId(), recommendedStock.getSymbol(), recommendedStock.getSectorString());
        
        return recommendedStock;
    }

    /**
     * 각 주식의 가중치를 계산합니다.
     * 점수 기반 가중치와 sector 다양성 가중치를 결합합니다.
     * 
     * 성능 최적화: 점수 맵을 파라미터로 받아 메모리에서 조회 (DB 쿼리 없음)
     */
    private List<StockWithWeight> calculateWeights(List<Stock> stocks, Map<Integer, Score> scoreMap) {
        // Sector별 분포 계산 (다양성 확보를 위해)
        // 국내/해외 섹터를 모두 고려하여 문자열로 통합
        Map<String, Long> sectorCounts = stocks.stream()
                .collect(Collectors.groupingBy(
                        stock -> {
                            if (stock.getDomesticSector() != null && stock.getDomesticSector() != DomesticSector.UNKNOWN) {
                                return "DOMESTIC_" + stock.getDomesticSector().getName();
                            } else if (stock.getOverseasSector() != null && stock.getOverseasSector() != OverseasSector.UNKNOWN) {
                                return "OVERSEAS_" + stock.getOverseasSector().getName();
                            }
                            return "UNKNOWN";
                        },
                        Collectors.counting()
                ));
        
        long totalStocks = stocks.size();
        
        return stocks.stream()
                .map(stock -> {
                    // 1. 점수 기반 가중치 (0-100점을 1-11 가중치로 변환, 부드러운 곡선)
                    // 이미 필터링되었으므로 점수가 반드시 존재함
                    Score score = scoreMap.get(stock.getId());
                    if (score == null) {
                        throw new IllegalStateException("주식(id:" + stock.getId() + ")에 점수가 없습니다.");
                    }
                    int scoreValue = getScoreByCountry(score, stock.getExchangeNum());
                    double scoreWeight = calculateScoreWeight(scoreValue);
                    
                    // 2. Sector 다양성 가중치 (적게 나온 sector에 더 높은 가중치)
                    String sectorKey = stock.getDomesticSector() != null && stock.getDomesticSector() != DomesticSector.UNKNOWN
                            ? "DOMESTIC_" + stock.getDomesticSector().getName()
                            : (stock.getOverseasSector() != null && stock.getOverseasSector() != OverseasSector.UNKNOWN
                                ? "OVERSEAS_" + stock.getOverseasSector().getName()
                                : "UNKNOWN");
                    long sectorCount = sectorCounts.getOrDefault(sectorKey, 1L);
                    double sectorWeight = calculateSectorDiversityWeight(sectorCount, totalStocks);
                    
                    // 최종 가중치 = 점수 가중치 * sector 가중치
                    double totalWeight = scoreWeight * sectorWeight;
                    
                    return new StockWithWeight(stock, totalWeight);
                })
                .collect(Collectors.toList());
    }

    /**
     * 국가별 점수를 반환합니다.
     */
    private int getScoreByCountry(Score score, EXCHANGENUM exchangeNum) {
        boolean isKorea = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
                .contains(exchangeNum);
        return isKorea ? score.getScoreKorea() : score.getScoreOversea();
    }

    /**
     * 점수 기반 가중치를 계산합니다.
     * 점수가 높을수록 선택 확률이 증가하지만, 너무 극단적이지 않도록 부드러운 곡선 적용.
     * 0점: 1.0, 50점: 6.0, 100점: 11.0 (제곱근 곡선 사용)
     */
    private double calculateScoreWeight(int score) {
        // 점수를 0-100 범위로 제한
        score = Math.max(0, Math.min(100, score));
        
        // 제곱근 곡선: sqrt(score/100) * 10 + 1
        // 0점 -> 1.0, 50점 -> 8.07, 100점 -> 11.0
        return Math.sqrt(score / 100.0) * 10.0 + 1.0;
    }

    /**
     * Sector 다양성 가중치를 계산합니다.
     * 적게 나온 sector에 더 높은 가중치를 부여하여 다양성을 확보합니다.
     */
    private double calculateSectorDiversityWeight(long sectorCount, long totalStocks) {
        if (totalStocks == 0) return 1.0;
        
        // 평균 섹터 개수보다 적게 나온 섹터에 보너스 가중치
        // 국내/해외 섹터를 모두 고려 (대략적인 평균 계산)
        int totalSectorTypes = DomesticSector.values().length + OverseasSector.values().length;
        double avgSectorCount = totalStocks / (double) totalSectorTypes;
        double ratio = avgSectorCount / Math.max(sectorCount, 1.0);
        
        // 0.8 ~ 1.5 범위로 제한 (너무 극단적이지 않게)
        return Math.max(0.8, Math.min(1.5, 1.0 + (ratio - 1.0) * 0.5));
    }

    /**
     * 가중치 기반 랜덤 선택을 수행합니다.
     */
    private Stock selectWeightedRandom(List<StockWithWeight> stocksWithWeight, Random random) {
        if (stocksWithWeight.isEmpty()) {
            throw new IllegalStateException("추천할 주식이 없습니다.");
        }
        
        // 총 가중치 계산
        double totalWeight = stocksWithWeight.stream()
                .mapToDouble(sw -> sw.weight)
                .sum();
        
        // 랜덤 값 생성 (0 ~ totalWeight)
        double randomValue = random.nextDouble() * totalWeight;
        
        // 누적 가중치를 따라 선택
        double cumulativeWeight = 0.0;
        for (StockWithWeight sw : stocksWithWeight) {
            cumulativeWeight += sw.weight;
            if (randomValue <= cumulativeWeight) {
                return sw.stock;
            }
        }
        
        // 마지막 주식 반환 (반올림 오차 대비)
        return stocksWithWeight.get(stocksWithWeight.size() - 1).stock;
    }

    /**
     * 가중치 기반 랜덤 선택을 여러 번 수행하여 중복 없이 여러 개를 선택합니다.
     * 선택된 주식은 후보 목록에서 제거하여 중복을 방지합니다.
     */
    private List<Stock> selectMultipleWeightedRandom(List<StockWithWeight> stocksWithWeight, Random random, int count) {
        if (stocksWithWeight.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 요청 개수가 후보 개수보다 많으면 후보 개수만큼만 반환
        int actualCount = Math.min(count, stocksWithWeight.size());
        
        // 후보 목록을 복사하여 사용 (원본 보존)
        List<StockWithWeight> remainingCandidates = new ArrayList<>(stocksWithWeight);
        List<Stock> selectedStocks = new ArrayList<>();
        
        // 중복 없이 여러 개 선택
        for (int i = 0; i < actualCount; i++) {
            if (remainingCandidates.isEmpty()) {
                break;
            }
            
            // 가중치 기반 랜덤 선택
            Stock selected = selectWeightedRandom(remainingCandidates, random);
            selectedStocks.add(selected);
            
            // 선택된 주식은 후보 목록에서 제거 (중복 방지)
            remainingCandidates.removeIf(sw -> sw.stock.getId().equals(selected.getId()));
        }
        
        return selectedStocks;
    }

    /**
     * 주식과 가중치를 함께 담는 내부 클래스
     */
    private static class StockWithWeight {
        final Stock stock;
        final double weight;

        StockWithWeight(Stock stock, double weight) {
            this.stock = stock;
            this.weight = weight;
        }
    }

    /**
     * 사용자에게 추천할 주식들을 5개 반환합니다.
     * 가중치 기반 랜덤 선택을 사용하여 다양성을 확보하고, 중복을 방지합니다.
     * 이전 추천과의 중복을 완전히 방지합니다.
     * 
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티 리스트 (최대 5개)
     */
    public List<Stock> getRecommendedStocks(User user) {
        final int RECOMMEND_COUNT = 5;
        log.info("사용자(id:{})에게 가중치 기반 주식 추천을 시작합니다. (추천 개수: {})", user.getId(), RECOMMEND_COUNT);
        
        // 사용자가 "다시 보지 않음"으로 설정한 종목 ID 목록 조회 (성능 최적화: stockId만 직접 조회)
        Set<Integer> hiddenStockIds = new HashSet<>(
                preferenceRepository.findStockIdsByUserIdAndPreferenceType(user.getId(), PreferenceType.NEVER_SHOW)
        );
        
        // 이전에 추천한 종목 ID 목록 조회 (중복 방지)
        Set<Integer> recentStockIds = recentRecommendations.getOrDefault(user.getId(), new HashSet<>());
        
        log.info("사용자(id:{})가 숨긴 종목 개수: {}, 이전 추천 종목 개수: {}", 
                user.getId(), hiddenStockIds.size(), recentStockIds.size());
        
        LocalDate today = LocalDate.now();
        
        // valid = true인 주식만 조회 (성능 최적화: DB에서 필터링)
        List<Stock> validStocks = stockRepository.findAllValidStocks();
        
        // 숨긴 종목과 이전 추천 종목 제외
        List<Stock> candidateStocks = validStocks.stream()
                .filter(stock -> !hiddenStockIds.contains(stock.getId()))
                .filter(stock -> !recentStockIds.contains(stock.getId()))
                .collect(Collectors.toList());
        
        if (candidateStocks.isEmpty()) {
            log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. (valid=true인 종목 없음 또는 모두 이전에 추천함)", user.getId());
            // 이전 추천 기록을 초기화하여 새로운 추천 가능하도록 함
            recentRecommendations.remove(user.getId());
            // 다시 시도 (이번엔 이전 추천 제외 없이)
            candidateStocks = validStocks.stream()
                    .filter(stock -> !hiddenStockIds.contains(stock.getId()))
                    .collect(Collectors.toList());
            if (candidateStocks.isEmpty()) {
                return Collections.emptyList();
            }
        }
        
        // 배치로 점수 조회 (N+1 문제 해결)
        List<Integer> candidateStockIds = candidateStocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());
        
        // 오늘 날짜 점수와 최신 점수를 배치로 조회
        List<Score> todayScores = scoreRepository.findTodayScoresByStockIds(candidateStockIds, today);
        List<Score> latestScores = scoreRepository.findLatestScoresByStockIds(candidateStockIds);
        
        // stockId -> Score 맵 생성 (오늘 점수 우선, 없으면 최신 점수)
        Map<Integer, Score> scoreMap = new HashMap<>();
        todayScores.forEach(score -> scoreMap.put(score.getStockId(), score));
        latestScores.forEach(score -> scoreMap.putIfAbsent(score.getStockId(), score));
        
        // 점수가 있는 주식만 필터링
        List<Stock> stocksWithScore = candidateStocks.stream()
                .filter(stock -> scoreMap.containsKey(stock.getId()))
                .collect(Collectors.toList());
        
        if (stocksWithScore.isEmpty()) {
            log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. (valid=true이고 점수가 있는 종목 없음)", user.getId());
            return Collections.emptyList();
        }
        
        log.info("추천 대상 주식 개수: {}개 (valid=true, 점수 있음, 이전 추천 제외)", stocksWithScore.size());
        
        // 각 주식의 가중치 계산 (점수 맵을 전달하여 메모리에서 조회)
        List<StockWithWeight> stocksWithWeight = calculateWeights(stocksWithScore, scoreMap);
        
        // 가중치 기반 랜덤 선택으로 중복 없이 여러 개 선택
        Random random = new Random(System.currentTimeMillis() + user.getId());
        List<Stock> recommendedStocks = selectMultipleWeightedRandom(stocksWithWeight, random, RECOMMEND_COUNT);
        
        // 추천한 종목을 메모리 캐시에 저장 (이전 추천과 중복 방지)
        saveRecentRecommendations(user.getId(), recommendedStocks);
        
        log.info("사용자(id:{})에게 주식 {}개 가중치 기반 추천 완료", user.getId(), recommendedStocks.size());
        
        return recommendedStocks;
    }
    
    /**
     * 사용자의 최근 추천 종목을 메모리 캐시에 저장합니다.
     * 최대 개수를 초과하면 일부 항목을 제거합니다.
     */
    private void saveRecentRecommendations(Integer userId, List<Stock> recommendedStocks) {
        Set<Integer> recentStocks = recentRecommendations.computeIfAbsent(userId, k -> new HashSet<>());
        
        // 새로 추천한 종목 추가
        for (Stock stock : recommendedStocks) {
            recentStocks.add(stock.getId());
        }
        
        // 최대 개수 초과 시 오래된 항목 일부 제거 (간단한 처리)
        if (recentStocks.size() > MAX_RECENT_RECOMMENDATIONS) {
            // 최대 개수의 80%만 유지 (오래된 20% 제거)
            int targetSize = (int) (MAX_RECENT_RECOMMENDATIONS * 0.8);
            List<Integer> stockIdList = new ArrayList<>(recentStocks);
            // 앞쪽 일부만 제거 (간단한 FIFO 방식)
            List<Integer> toKeep = stockIdList.subList(stockIdList.size() - targetSize, stockIdList.size());
            recentStocks.clear();
            recentStocks.addAll(toKeep);
        }
    }

    /**
     * 동기적으로 실시간 주식 가격 정보를 가져오는 메서드입니다.
     */
    public StockInfoResponse getRealTimeStockPriceSync(Stock stock) {
        return securityService.getRealTimeStockPrice(stock).block();
    }
}
