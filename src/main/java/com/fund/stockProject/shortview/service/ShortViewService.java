package com.fund.stockProject.shortview.service;

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

/**
 * 🚀 숏뷰 주식 추천 서비스
 *
 * 현재는 간단한 랜덤 추천 시스템을 사용합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShortViewService {

    private final StockRepository stockRepository;
    private final SecurityService securityService;

    /**
     * 사용자에게 추천할 주식 엔티티를 반환하는 메인 메서드입니다.
     * @param user 현재 로그인한 사용자
     * @return 추천된 주식(Stock) 엔티티
     */
    public Stock getRecommendedStock(User user) {
        log.info("사용자(id:{})에게 랜덤 주식 추천을 시작합니다.", user.getId());
        
        // 전체 주식 개수 조회
        long totalCount = stockRepository.count();
        
        if (totalCount == 0) {
            log.warn("데이터베이스에 주식이 없습니다.");
            return null;
        }
        
        // 랜덤 인덱스 생성 (사용자 ID를 시드로 사용해서 같은 사용자는 같은 주식을 받지 않도록)
        Random random = new Random(System.currentTimeMillis() + user.getId());
        long randomOffset = random.nextInt((int) Math.min(totalCount, 1000)); // 최대 1000개까지만
        
        // 랜덤 오프셋을 사용해서 주식 조회
        List<Stock> randomStocks = stockRepository.findAll(PageRequest.of((int) (randomOffset / 20), 20)).getContent();
        
        if (randomStocks.isEmpty()) {
            log.warn("랜덤 주식 조회 실패, 첫 번째 주식을 반환합니다.");
            return stockRepository.findTop20ByOrderByCreatedAtDesc().stream().findFirst().orElse(null);
        }
        
        // 조회된 주식들 중에서 다시 랜덤 선택
        int randomIndex = random.nextInt(randomStocks.size());
        Stock recommendedStock = randomStocks.get(randomIndex);
        
        log.info("사용자(id:{})에게 주식(id:{}, symbol:{}) 랜덤 추천 완료", 
                user.getId(), recommendedStock.getId(), recommendedStock.getSymbol());
        
        return recommendedStock;
    }

    /**
     * 동기적으로 실시간 주식 가격 정보를 가져오는 메서드입니다.
     */
    public StockInfoResponse getRealTimeStockPriceSync(Stock stock) {
        return securityService.getRealTimeStockPrice(stock).block();
    }
}
