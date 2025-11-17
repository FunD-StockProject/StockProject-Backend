package com.fund.stockProject.stock.controller;

import com.fund.stockProject.stock.service.StockImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
@Tag(name = "주식 관리 (Stock Admin)", description = "주식 종목 데이터 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class StockImportController {

    private final StockImportService stockImportService;

    @PostMapping("/import")
    @Operation(summary = "종목 데이터 임포트", description = "JSON 파일에서 종목 데이터를 읽어서 DB에 저장합니다. 기본 경로: scripts/stocks_data.json")
    public ResponseEntity<Map<String, String>> importStocks(
            @RequestParam(required = false, defaultValue = "scripts/stocks_data.json") String jsonFilePath) {
        try {
            stockImportService.importStocksFromJson(jsonFilePath);
            return ResponseEntity.ok(Map.of("message", "Stocks imported successfully"));
        } catch (Exception e) {
            log.error("Error importing stocks", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to import stocks: " + e.getMessage()));
        }
    }
}

