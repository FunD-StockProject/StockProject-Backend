package com.fund.stockProject.shortview.service;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.domain.SECTOR;
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
import java.util.stream.Collectors;

/**
 * 🚀 숏뷰 주식 추천 서비스
 *
 * 점수 기반 가중치 랜덤 추천 시스템을 사용합니다.
 * - 높은 인간지표 점수일수록 선택 확률 증가
 * - Sector 다양성을 고려한 추천
 * - "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
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

    /**
     * 사용자에게 추천할 주식 엔티티를 반환하는 메인 메서드입니다.
     * 점수와 sector 기반 가중치 랜덤 추천을 사용합니다.
     * "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티
     */
    public Stock getRecommendedStock(User user) {
        log.info("사용자(id:{})에게 가중치 기반 주식 추천을 시작합니다.", user.getId());
        
        // 사용자가 "다시 보지 않음"으로 설정한 종목 ID 목록 조회
        List<Integer> hiddenStockIds = preferenceRepository.findByUserIdAndPreferenceType(user.getId(), PreferenceType.NEVER_SHOW)
                .stream()
                .map(preference -> preference.getStock().getId())
                .collect(Collectors.toList());
        
        log.info("사용자(id:{})가 숨긴 종목 개수: {}", user.getId(), hiddenStockIds.size());
        
        LocalDate today = LocalDate.now();
        
        // valid = true이고 점수가 있는 주식만 조회 (숨긴 종목 제외)
        List<Stock> validStocks = stockRepository.findAll().stream()
                .filter(stock -> stock.getValid() != null && stock.getValid()) // valid = true만
                .filter(stock -> !hiddenStockIds.contains(stock.getId())) // 숨긴 종목 제외
                .filter(stock -> hasScore(stock, today)) // 점수가 있는 주식만
                .collect(Collectors.toList());
        
        if (validStocks.isEmpty()) {
            log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. (valid=true이고 점수가 있는 종목 없음)", user.getId());
            return null;
        }
        
        log.info("추천 대상 주식 개수: {}개 (valid=true, 점수 있음)", validStocks.size());
        
        // 각 주식의 가중치 계산
        List<StockWithWeight> stocksWithWeight = calculateWeights(validStocks, today);
        
        // 가중치 기반 랜덤 선택
        Random random = new Random(System.currentTimeMillis() + user.getId());
        Stock recommendedStock = selectWeightedRandom(stocksWithWeight, random);
        
        log.info("사용자(id:{})에게 주식(id:{}, symbol:{}, sector:{}) 가중치 기반 추천 완료", 
                user.getId(), recommendedStock.getId(), recommendedStock.getSymbol(), recommendedStock.getSector());
        
        return recommendedStock;
    }

    /**
     * 주식에 점수가 있는지 확인합니다.
     */
    private boolean hasScore(Stock stock, LocalDate today) {
        // 오늘 날짜 점수 우선 확인
        Optional<Score> todayScore = scoreRepository.findByStockIdAndDate(stock.getId(), today);
        if (todayScore.isPresent()) {
            return true;
        }
        
        // 없으면 최신 점수 확인
        Optional<Score> latestScore = scoreRepository.findTopByStockIdOrderByDateDesc(stock.getId());
        return latestScore.isPresent();
    }

    /**
     * 각 주식의 가중치를 계산합니다.
     * 점수 기반 가중치와 sector 다양성 가중치를 결합합니다.
     */
    private List<StockWithWeight> calculateWeights(List<Stock> stocks, LocalDate today) {
        // Sector별 분포 계산 (다양성 확보를 위해)
        Map<SECTOR, Long> sectorCounts = stocks.stream()
                .collect(Collectors.groupingBy(
                        stock -> stock.getSector() != null ? stock.getSector() : SECTOR.UNKNOWN,
                        Collectors.counting()
                ));
        
        long totalStocks = stocks.size();
        
        return stocks.stream()
                .map(stock -> {
                    // 1. 점수 기반 가중치 (0-100점을 1-11 가중치로 변환, 부드러운 곡선)
                    // 이미 필터링되었으므로 점수가 반드시 존재함
                    int score = getLatestScore(stock, today);
                    double scoreWeight = calculateScoreWeight(score);
                    
                    // 2. Sector 다양성 가중치 (적게 나온 sector에 더 높은 가중치)
                    SECTOR sector = stock.getSector() != null ? stock.getSector() : SECTOR.UNKNOWN;
                    long sectorCount = sectorCounts.getOrDefault(sector, 1L);
                    double sectorWeight = calculateSectorDiversityWeight(sectorCount, totalStocks);
                    
                    // 최종 가중치 = 점수 가중치 * sector 가중치
                    double totalWeight = scoreWeight * sectorWeight;
                    
                    return new StockWithWeight(stock, totalWeight);
                })
                .collect(Collectors.toList());
    }

    /**
     * 주식의 최신 점수를 조회합니다.
     * 점수가 반드시 존재한다고 가정합니다. (필터링 후 호출되므로)
     */
    private int getLatestScore(Stock stock, LocalDate today) {
        // 오늘 날짜 점수 우선 조회
        Optional<Score> todayScore = scoreRepository.findByStockIdAndDate(stock.getId(), today);
        if (todayScore.isPresent()) {
            return getScoreByCountry(todayScore.get(), stock.getExchangeNum());
        }
        
        // 없으면 최신 점수 조회 (반드시 존재함)
        Optional<Score> latestScore = scoreRepository.findTopByStockIdOrderByDateDesc(stock.getId());
        if (latestScore.isPresent()) {
            return getScoreByCountry(latestScore.get(), stock.getExchangeNum());
        }
        
        // 점수가 없으면 예외 발생 (이 경우는 발생하지 않아야 함)
        throw new IllegalStateException("주식(id:" + stock.getId() + ")에 점수가 없습니다. valid=true 필터링 후에는 점수가 반드시 있어야 합니다.");
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
        double avgSectorCount = totalStocks / (double) SECTOR.values().length;
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
     * 동기적으로 실시간 주식 가격 정보를 가져오는 메서드입니다.
     */
    public StockInfoResponse getRealTimeStockPriceSync(Stock stock) {
        return securityService.getRealTimeStockPrice(stock).block();
    }
}
