package com.fund.stockProject.shortview.service;

import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.shortview.dto.ShortViewResponse;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import com.fund.stockProject.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 🚀 숏뷰 주식 추천 서비스
 *
 * 현재는 간단한 랜덤 추천 시스템을 사용합니다.
 * "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortViewService {

    private final StockRepository stockRepository;
    private final SecurityService securityService;
    private final PreferenceRepository preferenceRepository;

    /**
     * 사용자에게 추천할 주식 엔티티를 반환하는 메인 메서드입니다.
     * "다시 보지 않음"으로 설정된 종목은 추천에서 제외합니다.
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티
     */
    public Stock getRecommendedStock(User user) {
        log.info("사용자(id:{})에게 랜덤 주식 추천을 시작합니다.", user.getId());
        
        // 사용자가 "다시 보지 않음"으로 설정한 종목 ID 목록 조회
        List<Integer> hiddenStockIds = preferenceRepository.findByUserIdAndPreferenceType(user.getId(), PreferenceType.NEVER_SHOW)
                .stream()
                .map(preference -> preference.getStock().getId())
                .collect(Collectors.toList());
        
        log.info("사용자(id:{})가 숨긴 종목 개수: {}", user.getId(), hiddenStockIds.size());
        
        // 전체 주식 개수 조회 (숨긴 종목 제외)
        long totalCount = stockRepository.count();
        
        if (totalCount == 0) {
            log.warn("데이터베이스에 주식이 없습니다.");
            return null;
        }
        
        // 랜덤 인덱스 생성 (사용자 ID를 시드로 사용해서 같은 사용자는 같은 주식을 받지 않도록)
        Random random = new Random(System.currentTimeMillis() + user.getId());
        
        // 최대 10번 시도하여 숨기지 않은 종목 찾기
        for (int attempt = 0; attempt < 10; attempt++) {
            long randomOffset = random.nextInt((int) Math.min(totalCount, 1000)); // 최대 1000개까지만
            
            // 랜덤 오프셋을 사용해서 주식 조회
            List<Stock> randomStocks = stockRepository.findAll(PageRequest.of((int) (randomOffset / 20), 20)).getContent();
            
            if (randomStocks.isEmpty()) {
                log.warn("랜덤 주식 조회 실패, 첫 번째 주식을 반환합니다.");
                Stock firstStock = stockRepository.findTop20ByOrderByCreatedAtDesc().stream().findFirst().orElse(null);
                if (firstStock != null && !hiddenStockIds.contains(firstStock.getId())) {
                    return firstStock;
                }
                continue;
            }
            
            // 숨기지 않은 종목들만 필터링
            List<Stock> availableStocks = randomStocks.stream()
                    .filter(stock -> !hiddenStockIds.contains(stock.getId()))
                    .collect(Collectors.toList());
            
            if (!availableStocks.isEmpty()) {
                // 조회된 주식들 중에서 다시 랜덤 선택
                int randomIndex = random.nextInt(availableStocks.size());
                Stock recommendedStock = availableStocks.get(randomIndex);
                
                log.info("사용자(id:{})에게 주식(id:{}, symbol:{}) 랜덤 추천 완료", 
                        user.getId(), recommendedStock.getId(), recommendedStock.getSymbol());
                
                return recommendedStock;
            }
        }
        
        // 10번 시도해도 숨기지 않은 종목을 찾지 못한 경우, 전체 종목에서 숨기지 않은 종목 찾기
        log.warn("랜덤 추천 실패, 전체 종목에서 숨기지 않은 종목을 찾습니다.");
        List<Stock> allStocks = stockRepository.findAll();
        List<Stock> availableStocks = allStocks.stream()
                .filter(stock -> !hiddenStockIds.contains(stock.getId()))
                .collect(Collectors.toList());
        
        if (!availableStocks.isEmpty()) {
            int randomIndex = random.nextInt(availableStocks.size());
            Stock recommendedStock = availableStocks.get(randomIndex);
            
            log.info("사용자(id:{})에게 주식(id:{}, symbol:{}) 전체 검색 추천 완료", 
                    user.getId(), recommendedStock.getId(), recommendedStock.getSymbol());
            
            return recommendedStock;
        }
        
        log.warn("사용자(id:{})에게 추천할 수 있는 종목이 없습니다. 모든 종목이 숨겨져 있습니다.", user.getId());
        return null;
    }

    /**
     * 동기적으로 실시간 주식 가격 정보를 가져오는 메서드입니다.
     */
    public StockInfoResponse getRealTimeStockPriceSync(Stock stock) {
        return securityService.getRealTimeStockPrice(stock).block();
    }
}
