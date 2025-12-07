package com.fund.stockProject.stock.controller;

import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.stock.service.StockImportService;
import com.fund.stockProject.stock.service.StockMasterUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final StockMasterUpdateService stockMasterUpdateService;

    @PostMapping("/import")
    @Operation(summary = "종목 데이터 임포트", description = "JSON 파일에서 종목 데이터를 읽어서 DB에 저장합니다. 기본 경로: scripts/stocks_data.json")
    public ResponseEntity<Map<String, String>> importStocks(
            @RequestParam(required = false, defaultValue = "scripts/stocks_data.json") String jsonFilePath,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        try {
            // ADMIN 권한 체크
            if (customUserDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }
            
            boolean isAdmin = customUserDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only ADMIN users can import stocks"));
            }
            
            stockImportService.importStocksFromJson(jsonFilePath);
            return ResponseEntity.ok(Map.of("message", "Stocks imported successfully"));
        } catch (Exception e) {
            log.error("Error importing stocks", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to import stocks: " + e.getMessage()));
        }
    }

    @PostMapping("/update-master")
    @Operation(
            summary = "종목 마스터 업데이트 (배치 작업)",
            description = "Python 스크립트를 실행하여 최신 종목 데이터를 수집하고 DB에 반영합니다. ADMIN만 가능합니다."
    )
    public ResponseEntity<Map<String, String>> updateStockMaster(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails) {
        try {
            // ADMIN 권한 체크
            if (customUserDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }
            
            boolean isAdmin = customUserDetails.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only ADMIN users can trigger stock master update"));
            }
            
            stockMasterUpdateService.updateStockMaster();
            return ResponseEntity.ok(Map.of("message", "Stock master update completed successfully"));
        } catch (Exception e) {
            log.error("Error updating stock master", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update stock master: " + e.getMessage()));
        }
    }
}

