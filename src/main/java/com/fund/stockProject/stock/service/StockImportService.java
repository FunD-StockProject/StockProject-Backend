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
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

            // 시퀀스 동기화: 실제 DB의 최대 ID로 시퀀스 설정
            synchronizeSequence();

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
            
            // 모든 종목을 symbol 기준으로 UPSERT 처리 (단순하고 안전한 방법)
            if (!stocksToSaveMap.isEmpty()) {
                int processedCount = 0;
                int updatedCount = 0;
                int insertedCount = 0;
                int errorCount = 0;
                
                for (Stock stock : stocksToSaveMap.values()) {
                    try {
                        // 이전 작업을 DB에 반영
                        entityManager.flush();
                        entityManager.clear();
                        
                        // 항상 DB에서 symbol로 확인 (가장 확실한 방법)
                        Optional<Stock> existingStockOpt = stockRepository.findBySymbol(stock.getSymbol());
                        
                        if (existingStockOpt.isPresent()) {
                            // 기존 종목 업데이트
                            Stock existing = existingStockOpt.get();
                            existing.updateSymbolNameIfNull(stock.getSymbolName());
                            existing.setValid(true);
                            if (stock.getDomesticSector() != null) {
                                existing.setDomesticSector(stock.getDomesticSector());
                            }
                            if (stock.getOverseasSector() != null) {
                                existing.setOverseasSector(stock.getOverseasSector());
                            }
                            stockRepository.save(existing);
                            entityManager.flush();
                            updatedCount++;
                            log.debug("Updated existing stock: {}", stock.getSymbol());
                        } else {
                            // 새 종목 저장
                            // ID가 null인 새 엔티티이므로 persist()가 호출됨
                            // 시퀀스에서 ID를 생성하지만, 혹시 모를 중복을 대비해 예외 처리
                            try {
                                stockRepository.save(stock);
                                entityManager.flush();
                                entityManager.detach(stock); // 영속성 컨텍스트에서 분리
                                insertedCount++;
                                log.debug("Inserted new stock: {}", stock.getSymbol());
                            } catch (DataIntegrityViolationException e) {
                                // 중복 키 오류 발생 시 기존 종목으로 처리
                                entityManager.clear();
                                if (handleDuplicateKeyError(stock, e)) {
                                    updatedCount++;
                                    log.debug("Updated stock after duplicate key error: {}", stock.getSymbol());
                                } else {
                                    errorCount++;
                                    log.warn("Failed to handle duplicate key error for stock: {}", stock.getSymbol());
                                }
                            } catch (Exception e) {
                                // 예외 메시지에 "Duplicate entry"가 포함되어 있는지 확인
                                String errorMessage = getRootCauseMessage(e);
                                if (errorMessage != null && errorMessage.contains("Duplicate entry")) {
                                    entityManager.clear();
                                    if (handleDuplicateKeyError(stock, e)) {
                                        updatedCount++;
                                        log.debug("Updated stock after duplicate key error: {}", stock.getSymbol());
                                    } else {
                                        errorCount++;
                                        log.warn("Failed to handle duplicate key error for stock: {}", stock.getSymbol());
                                    }
                                } else {
                                    errorCount++;
                                    log.warn("Failed to save stock: {} - {}", stock.getSymbol(), errorMessage);
                                }
                            }
                        }
                        processedCount++;
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Unexpected error processing stock: {}", stock.getSymbol(), e);
                    }
                }
                
                log.info("Processed {} stocks - Inserted: {}, Updated: {}, Errors: {}", 
                        processedCount, insertedCount, updatedCount, errorCount);
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

    /**
     * 중복 키 오류 발생 시 기존 종목을 찾아 업데이트합니다.
     * @return 처리 성공 여부
     */
    private boolean handleDuplicateKeyError(Stock stock, Exception e) {
        log.warn("Duplicate key detected for stock: {}, attempting to update existing", stock.getSymbol());
        try {
            // 예외 메시지에서 ID 추출 시도
            Integer duplicateId = extractIdFromException(e);
            log.debug("Extracted duplicate ID from exception: {} for stock: {}", duplicateId, stock.getSymbol());
            
            Stock existing = null;
            
            // 1. 먼저 symbol로 조회 (가장 확실한 방법)
            Optional<Stock> existingBySymbol = stockRepository.findBySymbol(stock.getSymbol());
            if (existingBySymbol.isPresent()) {
                existing = existingBySymbol.get();
                log.debug("Found existing stock by symbol: {} (ID: {})", stock.getSymbol(), existing.getId());
            }
            
            // 2. symbol로 찾지 못했고 ID가 추출되었으면 ID로 조회
            if (existing == null && duplicateId != null) {
                Optional<Stock> existingById = stockRepository.findById(duplicateId);
                if (existingById.isPresent()) {
                    existing = existingById.get();
                    log.debug("Found existing stock by ID: {} (Symbol: {})", duplicateId, existing.getSymbol());
                } else {
                    log.debug("Stock with ID {} not found in repository", duplicateId);
                }
            }
            
            if (existing != null) {
                existing.updateSymbolNameIfNull(stock.getSymbolName());
                existing.setValid(true);
                if (stock.getDomesticSector() != null) {
                    existing.setDomesticSector(stock.getDomesticSector());
                }
                if (stock.getOverseasSector() != null) {
                    existing.setOverseasSector(stock.getOverseasSector());
                }
                stockRepository.save(existing);
                entityManager.flush();
                log.debug("Stock updated after duplicate key error: {} (ID: {})", stock.getSymbol(), existing.getId());
                return true;
            } else {
                log.warn("Failed to find existing stock after duplicate key error: {} (extracted ID: {})", 
                        stock.getSymbol(), duplicateId);
                return false;
            }
        } catch (Exception ex) {
            log.error("Failed to handle duplicate key error for stock: {}", stock.getSymbol(), ex);
            return false;
        }
    }
    
    /**
     * 예외 메시지에서 중복된 ID를 추출합니다.
     * 예: "Duplicate entry '5130' for key 'stock.PRIMARY'" -> 5130
     */
    private Integer extractIdFromException(Exception e) {
        String message = getRootCauseMessage(e);
        
        if (message != null && message.contains("Duplicate entry")) {
            try {
                // "Duplicate entry '5130' for key" 패턴에서 ID 추출
                // 여러 패턴 시도: '5130', "5130", '5130'
                int startIdx = message.indexOf("'");
                if (startIdx >= 0) {
                    int endIdx = message.indexOf("'", startIdx + 1);
                    if (endIdx > startIdx) {
                        String idStr = message.substring(startIdx + 1, endIdx);
                        return Integer.parseInt(idStr);
                    }
                }
                // 작은따옴표로 찾지 못했으면 큰따옴표 시도
                startIdx = message.indexOf("\"");
                if (startIdx >= 0) {
                    int endIdx = message.indexOf("\"", startIdx + 1);
                    if (endIdx > startIdx) {
                        String idStr = message.substring(startIdx + 1, endIdx);
                        return Integer.parseInt(idStr);
                    }
                }
            } catch (NumberFormatException | StringIndexOutOfBoundsException ex) {
                log.debug("Failed to extract ID from exception message: {}", message);
            }
        }
        return null;
    }
    
    /**
     * 예외 체인을 따라가며 루트 원인 메시지를 추출합니다.
     */
    private String getRootCauseMessage(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        
        // 예외 체인을 따라가며 "Duplicate entry"가 포함된 메시지 찾기
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.contains("Duplicate entry")) {
                message = causeMessage;
                break;
            }
            cause = cause.getCause();
        }
        
        // 여전히 null이면 원본 예외 메시지 사용
        if (message == null) {
            message = e.getMessage();
        }
        
        return message;
    }

    /**
     * 시퀀스를 실제 DB의 최대 ID로 동기화합니다.
     * 이는 중복 키 오류를 방지하기 위해 필요합니다.
     * Hibernate가 MySQL에서 시퀀스를 사용할 때 생성하는 테이블을 업데이트합니다.
     */
    private void synchronizeSequence() {
        try {
            Integer maxId = stockRepository.findMaxId();
            if (maxId == null) {
                maxId = 0;
            }
            
            int nextVal = maxId + 1;
            
            // Hibernate가 생성하는 시퀀스 테이블 이름은 버전에 따라 다를 수 있습니다.
            // 일반적으로 hibernate_sequences 또는 sequence_name이 'stock_sequence'인 테이블을 사용합니다.
            
            // 방법 1: hibernate_sequences 테이블 업데이트 시도 (Hibernate 5+)
            try {
                Query updateQuery = entityManager.createNativeQuery(
                    "UPDATE hibernate_sequences SET next_val = :nextVal WHERE sequence_name = 'stock_sequence'"
                );
                updateQuery.setParameter("nextVal", nextVal);
                int updated = updateQuery.executeUpdate();
                
                if (updated == 0) {
                    // 레코드가 없으면 생성
                    Query insertQuery = entityManager.createNativeQuery(
                        "INSERT INTO hibernate_sequences (sequence_name, next_val) VALUES ('stock_sequence', :nextVal) " +
                        "ON DUPLICATE KEY UPDATE next_val = :nextVal"
                    );
                    insertQuery.setParameter("nextVal", nextVal);
                    insertQuery.executeUpdate();
                }
                log.info("Synchronized sequence to max ID: {} (next_val: {})", maxId, nextVal);
            } catch (Exception e1) {
                // hibernate_sequences 테이블이 없으면 다른 방법 시도
                log.debug("hibernate_sequences table not found, trying alternative method: {}", e1.getMessage());
                
                // 방법 2: stock_sequence 테이블 직접 업데이트 시도
                try {
                    Query updateQuery2 = entityManager.createNativeQuery(
                        "UPDATE stock_sequence SET next_val = :nextVal"
                    );
                    updateQuery2.setParameter("nextVal", nextVal);
                    int updated2 = updateQuery2.executeUpdate();
                    
                    if (updated2 == 0) {
                        Query insertQuery2 = entityManager.createNativeQuery(
                            "INSERT INTO stock_sequence (next_val) VALUES (:nextVal) " +
                            "ON DUPLICATE KEY UPDATE next_val = :nextVal"
                        );
                        insertQuery2.setParameter("nextVal", nextVal);
                        insertQuery2.executeUpdate();
                    }
                    log.info("Synchronized sequence to max ID: {} (next_val: {})", maxId, nextVal);
                } catch (Exception e2) {
                    log.warn("Failed to synchronize sequence using alternative method: {}", e2.getMessage());
                    // 시퀀스 동기화 실패는 치명적이지 않으므로 계속 진행
                }
            }
        } catch (Exception e) {
            // 시퀀스 동기화 실패는 치명적이지 않으므로 경고만 로깅
            log.warn("Failed to synchronize sequence: {}", e.getMessage());
        }
    }
}

