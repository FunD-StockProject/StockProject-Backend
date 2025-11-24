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

            // 1. 종목 마스터에 있는 종목 처리 (추가/업데이트)
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

                    EXCHANGENUM exchangeNum;
                    try {
                        exchangeNum = EXCHANGENUM.valueOf(exchangeNumStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid exchange number: {}, skipping stock: {}", exchangeNumStr, symbol);
                        skipped++;
                        continue;
                    }

                    // 섹터 코드 매핑 (KIS 업종 코드 → DomesticSector 또는 OverseasSector)
                    // KIS에서 제공하는 공식 업종 코드를 그대로 사용합니다.
                    if (sectorCodeStr != null && !sectorCodeStr.isEmpty() && !sectorCodeStr.equals("nan")) {
                        if (exchangeNum == EXCHANGENUM.KOSPI || exchangeNum == EXCHANGENUM.KOSDAQ) {
                            // 국내 주식: DomesticSector 사용
                            DomesticSector domesticSector = DomesticSector.fromCode(sectorCodeStr, exchangeNum);
                            if (domesticSector == DomesticSector.UNKNOWN) {
                                log.debug("Domestic sector mapping failed: code={}, exchange={}, stock={}. Using UNKNOWN.", 
                                    sectorCodeStr, exchangeNum, symbol);
                            } else {
                                log.trace("Mapped KIS sector code: {} -> DomesticSector: {} for stock: {}", 
                                    sectorCodeStr, domesticSector, symbol);
                            }
                        } else if (exchangeNum == EXCHANGENUM.NAS || exchangeNum == EXCHANGENUM.NYS || exchangeNum == EXCHANGENUM.AMS) {
                            // 해외 주식: OverseasSector 사용
                            OverseasSector overseasSector = OverseasSector.fromCode(sectorCodeStr);
                            if (overseasSector == OverseasSector.UNKNOWN) {
                                log.debug("Overseas sector mapping failed: code={}, exchange={}, stock={}. Using UNKNOWN.", 
                                    sectorCodeStr, exchangeNum, symbol);
                            } else {
                                log.trace("Mapped KIS sector code: {} -> OverseasSector: {} for stock: {}", 
                                    sectorCodeStr, overseasSector, symbol);
                            }
                        }
                    }

                    // 기존 종목 확인
                    Optional<Stock> existingStock = stockRepository.findBySymbol(symbol);
                    
                    if (existingStock.isPresent()) {
                        // 기존 종목 업데이트
                        Stock stock = existingStock.get();
                        stock.updateSymbolNameIfNull(symbolName);
                        // 종목 마스터에 있으므로 valid = true로 설정
                        setStockValid(stock, true);
                        // 섹터 업데이트
                        if (sectorCodeStr != null && !sectorCodeStr.isEmpty() && !sectorCodeStr.equals("nan")) {
                            stock.setSectorByExchange(sectorCodeStr, exchangeNum);
                        }
                        stockRepository.save(stock);
                        updated++;
                    } else {
                        // 새 종목 생성
                        Stock stock = createStock(symbol, symbolName, securityName, exchangeNum, true);
                        // 섹터 설정
                        if (sectorCodeStr != null && !sectorCodeStr.isEmpty() && !sectorCodeStr.equals("nan")) {
                            stock.setSectorByExchange(sectorCodeStr, exchangeNum);
                        }
                        stockRepository.save(stock);
                        saved++;
                    }

                } catch (Exception e) {
                    log.error("Error processing stock: {}", stockData, e);
                    skipped++;
                }
            }

            // 2. 종목 마스터에 없는 종목 찾기
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

