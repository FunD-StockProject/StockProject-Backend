package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.stock.service.StockMasterUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockUpdateScheduler {

    private final StockMasterUpdateService stockMasterUpdateService;

    /**
     * 매주 목요일 새벽 3시에 종목 마스터 데이터 업데이트
     * Python 스크립트를 실행하여 최신 종목 데이터를 수집하고 DB에 반영합니다.
     */
    @Scheduled(cron = "0 0 3 * * THU", zone = "Asia/Seoul") // 매주 목요일 3시 실행
    public void updateStockMaster() {
        log.info("Starting weekly stock master update scheduler");
        stockMasterUpdateService.updateStockMaster();
    }
}

