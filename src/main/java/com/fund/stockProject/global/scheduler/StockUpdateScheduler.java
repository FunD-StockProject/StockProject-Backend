package com.fund.stockProject.global.scheduler;

import com.fund.stockProject.stock.service.StockImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockUpdateScheduler {

    private final StockImportService stockImportService;

    /**
     * 매주 화요일 새벽 3시에 종목 마스터 데이터 업데이트
     * Python 스크립트를 실행하여 최신 종목 데이터를 수집하고 DB에 반영합니다.
     */
    @Scheduled(cron = "0 0 3 * * TUE", zone = "Asia/Seoul") // 매주 화요일 3시 실행
    public void updateStockMaster() {
        log.info("Starting weekly stock master update scheduler");
        
        try {
            // 1. Python 스크립트 실행하여 종목 데이터 수집
            String scriptPath = "scripts/import_stocks.py";
            File scriptFile = new File(scriptPath);
            
            // Docker 환경에서는 /app 경로 기준으로 실행
            if (!scriptFile.exists()) {
                scriptPath = "/app/scripts/import_stocks.py";
                scriptFile = new File(scriptPath);
            }
            
            if (!scriptFile.exists()) {
                log.error("Stock import script not found: {}", scriptPath);
                return;
            }

            log.info("Executing Python script: {}", scriptPath);
            // 스크립트 파일명만 사용 (작업 디렉토리를 스크립트 디렉토리로 설정하므로)
            String scriptFileName = scriptFile.getName();
            File scriptDir = scriptFile.getParentFile();
            
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptFileName);
            // 스크립트가 있는 디렉토리를 작업 디렉토리로 설정
            processBuilder.directory(scriptDir != null ? scriptDir : new File("."));
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // 스크립트 출력 읽기
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("Python script output: {}", line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python script execution failed with exit code: {}", exitCode);
                return;
            }
            
            log.info("Python script executed successfully");
            
            // 2. 생성된 JSON 파일을 DB에 반영
            String jsonFilePath = "scripts/stocks_data.json";
            File jsonFile = new File(jsonFilePath);
            // Docker 환경에서는 /app 경로 기준으로 확인
            if (!jsonFile.exists()) {
                jsonFilePath = "/app/scripts/stocks_data.json";
                jsonFile = new File(jsonFilePath);
            }
            if (!jsonFile.exists()) {
                log.error("Generated JSON file not found: {}", jsonFilePath);
                return;
            }
            log.info("Importing stocks from JSON file: {}", jsonFilePath);
            stockImportService.importStocksFromJson(jsonFilePath);
            
            log.info("Weekly stock master update completed successfully");
            
        } catch (Exception e) {
            log.error("Weekly stock master update scheduler failed", e);
            throw new RuntimeException("Failed to update stock master", e);
        }
    }
}

