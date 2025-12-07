package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.experiment.repository.ExperimentRepository;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockImportService {

    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;
    private final ExperimentRepository experimentRepository;
    private final PreferenceRepository preferenceRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * JSON 파일에서 종목 데이터를 읽어서 DB에 저장하고, 종목 마스터에 없는 종목은 isValid=false로 설정합니다.
     * 실험 등에서 사용된 종목은 보존합니다.
     * @param jsonFilePath JSON 파일 경로
     */
    @Transactional
    public void importStocksFromJson(String jsonFilePath) {
        try {
            File file = new File(jsonFilePath);
            if (!file.exists()) {
                log.error("JSON file not found: {}", jsonFilePath);
                return;
            }

            List<Map<String, Object>> stocksData = objectMapper.readValue(
                file,
                new TypeReference<List<Map<String, Object>>>() {}
            );

            log.info("Found {} stocks in JSON file", stocksData.size());

            // 종목 마스터에 있는 종목의 symbol 집합 생성
            Set<String> masterSymbols = stocksData.stream()
                .map(data -> (String) data.get("symbol"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            int saved = 0;
            int skipped = 0;
            int updated = 0;
            int invalidated = 0;
            int preserved = 0;

            // 기존 종목들을 symbol로 조회하여 Map으로 변환 (성능 최적화)
            // 조회 후 즉시 detach하여 영속성 컨텍스트 충돌 방지
            Map<String, Stock> existingStocksMap = new HashMap<>();
            List<Stock> allExistingStocks = stockRepository.findAll();
            for (Stock stock : allExistingStocks) {
                if (entityManager.contains(stock)) {
                    entityManager.detach(stock);
                }
                existingStocksMap.put(stock.getSymbol(), stock);
            }

            // 1. 종목 마스터에 있는 종목 처리 (추가/업데이트)
            // symbol을 키로 사용하여 중복 제거 (같은 symbol이 여러 번 나와도 한 번만 처리)
            Map<String, Stock> stocksToSaveMap = new LinkedHashMap<>();
            for (Map<String, Object> stockData : stocksData) {
                try {
                    String symbol = (String) stockData.get("symbol");
                    String symbolName = (String) stockData.get("symbolName");
                    String securityName = (String) stockData.get("securityName");
                    String exchangeNumStr = (String) stockData.get("exchangeNum");
                    String sectorCodeStr = (String) stockData.get("sectorCode");

                    if (symbol == null || symbolName == null || securityName == null || exchangeNumStr == null) {
                        log.warn("Skipping stock with missing required fields: {}", stockData);
                        skipped++;
                        continue;
                    }

                    // 이미 처리된 symbol이면 스킵 (중복 방지)
                    if (stocksToSaveMap.containsKey(symbol)) {
                        log.debug("Skipping duplicate symbol in JSON: {}", symbol);
                        continue;
                    }

                    EXCHANGENUM exchangeNum;
                    try {
                        exchangeNum = EXCHANGENUM.valueOf(exchangeNumStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid exchange number: {}, skipping stock: {}", exchangeNumStr, symbol);
                        skipped++;
                        continue;
                    }

                    Stock stock;
                    // 기존 종목 확인
                    if (existingStocksMap.containsKey(symbol)) {
                        // 기존 종목 업데이트
                        stock = existingStocksMap.get(symbol);
                        // 이미 detach된 상태이므로 추가 detach 불필요
                        stock.updateSymbolNameIfNull(symbolName);
                        // 종목 마스터에 있으므로 valid = true로 설정
                        setStockValid(stock, true);
                        updated++;
                    } else {
                        // 새 종목 생성
                        stock = createStock(symbol, symbolName, securityName, exchangeNum, true);
                        saved++;
                    }
                    
                    // 섹터 업데이트
                    if (sectorCodeStr != null && !sectorCodeStr.isEmpty() && !sectorCodeStr.equals("nan")) {
                        stock.setSectorByExchange(sectorCodeStr, exchangeNum);
                    }
                    
                    stocksToSaveMap.put(symbol, stock);

                } catch (Exception e) {
                    log.error("Error processing stock: {}", stockData, e);
                    skipped++;
                }
            }
            
            // 배치로 저장 - 기존 종목과 새 종목을 분리하여 처리
            if (!stocksToSaveMap.isEmpty()) {
                List<Stock> newStocks = new ArrayList<>();
                List<Stock> existingStocks = new ArrayList<>();
                
                // 기존 종목과 새 종목 분리
                for (Stock stock : stocksToSaveMap.values()) {
                    if (stock.getId() != null) {
                        existingStocks.add(stock);
                    } else {
                        newStocks.add(stock);
                    }
                }
                
                // 새 종목 저장
                if (!newStocks.isEmpty()) {
                    try {
                        entityManager.flush();
                        stockRepository.saveAll(newStocks);
                        entityManager.flush();
                        log.info("Saved {} new stocks", newStocks.size());
                    } catch (Exception e) {
                        log.error("Error saving new stocks: {}", e.getMessage());
                        throw e;
                    }
                }
                
                // 기존 종목 업데이트 - 개별 처리로 안전하게
                if (!existingStocks.isEmpty()) {
                    int updatedCount = 0;
                    int failedCount = 0;
                    for (Stock stock : existingStocks) {
                        try {
                            // ID로 다시 조회하여 영속성 컨텍스트에서 가져옴
                            Stock managedStock = stockRepository.findById(stock.getId())
                                    .orElse(null);
                            if (managedStock != null) {
                                // 조회한 엔티티에 변경사항 반영
                                managedStock.updateSymbolNameIfNull(stock.getSymbolName());
                                managedStock.setValid(stock.getValid());
                                if (stock.getDomesticSector() != null) {
                                    managedStock.setDomesticSector(stock.getDomesticSector());
                                }
                                if (stock.getOverseasSector() != null) {
                                    managedStock.setOverseasSector(stock.getOverseasSector());
                                }
                                stockRepository.save(managedStock);
                                updatedCount++;
                            } else {
                                log.warn("Stock not found for update: ID={}, Symbol={}", stock.getId(), stock.getSymbol());
                                failedCount++;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to update stock: ID={}, Symbol={} - {}", 
                                    stock.getId(), stock.getSymbol(), e.getMessage());
                            failedCount++;
                        }
                    }
                    entityManager.flush();
                    log.info("Updated {} existing stocks, {} failed", updatedCount, failedCount);
                }
            }

            // 2. 종목 마스터에 없는 종목 찾기
            // 영속성 컨텍스트를 정리한 후 조회하여 세션 안전성 보장
            entityManager.flush();
            entityManager.clear();
            List<Stock> allStocks = stockRepository.findAll();
            List<Integer> stockIdsToCheck = allStocks.stream()
                .filter(stock -> !masterSymbols.contains(stock.getSymbol()))
                .map(Stock::getId)
                .collect(Collectors.toList());

            if (!stockIdsToCheck.isEmpty()) {
                log.info("Found {} stocks not in master file", stockIdsToCheck.size());

                // 실험에서 사용된 종목 ID 조회 (배치)
                Set<Integer> usedInExperiments = new HashSet<>(
                    experimentRepository.findStockIdsUsedInExperiments(stockIdsToCheck)
                );

                // Preference에서 사용된 종목 ID 조회 (배치)
                Set<Integer> usedInPreferences = new HashSet<>(
                    preferenceRepository.findStockIdsUsedInPreferences(stockIdsToCheck)
                );

                // 종목 마스터에 없고, 실험/Preference에서도 사용되지 않은 종목만 invalid 처리
                for (Stock stock : allStocks) {
                    if (!masterSymbols.contains(stock.getSymbol())) {
                        Integer stockId = stock.getId();
                        
                        // 실험 또는 Preference에서 사용된 종목은 보존
                        if (usedInExperiments.contains(stockId) || usedInPreferences.contains(stockId)) {
                            log.debug("Preserving stock {} (used in experiments/preferences)", stock.getSymbol());
                            preserved++;
                            continue;
                        }

                        // 사용되지 않은 종목은 isValid = false로 설정
                        if (stock.getValid() == null || stock.getValid()) {
                            setStockValid(stock, false);
                            stockRepository.save(stock);
                            invalidated++;
                            log.debug("Invalidated stock: {} (not in master, not used)", stock.getSymbol());
                        }
                    }
                }
            }

            // 섹터 매핑 통계 계산
            long stocksWithSector = stockRepository.findAll().stream()
                .filter(s -> (s.getDomesticSector() != null && s.getDomesticSector() != DomesticSector.UNKNOWN) ||
                             (s.getOverseasSector() != null && s.getOverseasSector() != OverseasSector.UNKNOWN))
                .count();
            long totalStocks = stockRepository.count();
            double sectorMappingRate = totalStocks > 0 ? 
                (double) stocksWithSector / totalStocks * 100 : 0.0;

            log.info("Import completed - Saved: {}, Updated: {}, Skipped: {}, Invalidated: {}, Preserved: {}", 
                saved, updated, skipped, invalidated, preserved);
            log.info("Sector mapping statistics - Mapped: {}/{}, Rate: {:.2f}%", 
                stocksWithSector, totalStocks, sectorMappingRate);

        } catch (IOException e) {
            log.error("Error reading JSON file: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to import stocks from JSON", e);
        }
    }

    /**
     * Stock의 valid 필드를 설정합니다
     */
    private void setStockValid(Stock stock, boolean valid) {
        stock.setValid(valid);
    }

    /**
     * Stock 엔티티를 생성합니다.
     */
    private Stock createStock(String symbol, String symbolName, String securityName, 
                              EXCHANGENUM exchangeNum, Boolean valid) {
        return new Stock(symbol, symbolName, securityName, exchangeNum, valid);
    }
}

