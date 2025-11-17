package com.fund.stockProject.stock.mapper;

import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.domain.SECTOR;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * KIS Open API 업종 코드를 GICS Sector로 매핑하는 매퍼
 * 
 * ⚠️ 주의: 이 매핑은 샘플 데이터 기반 추론으로, 공식적인 것이 아닙니다.
 * 특히 국내 주식 코드는 여러 GICS 섹터가 혼재된 경우가 많아 정확도가 떨어질 수 있습니다.
 * 
 * 금융 투자 서비스에서 사용되므로, 매핑 실패 시 UNKNOWN을 반환하여
 * 부정확한 정보 제공을 방지합니다.
 * 
 */
@Slf4j
public class KisGicsMapper {

    // KOSPI 코드 매핑 (2자리)
    private static final Map<String, String> KOSPI_TO_GICS = Map.ofEntries(
        Map.entry("27", "35"),  // 헬스케어 (주의: 하이트진로(30) 혼재)
        Map.entry("21", "40"),  // 금융 (지주회사)
        Map.entry("16", "25"),  // 임의소비재
        Map.entry("18", "20"),  // 산업재
        Map.entry("26", "40"),  // 금융 (지주회사)
        Map.entry("19", "20"),  // 산업재
        Map.entry("28", "60"),  // 부동산
        Map.entry("29", "45"),  // 정보기술
        Map.entry("30", "50"),  // 통신서비스 (미디어)
        Map.entry("20", "50")   // 통신서비스 (통신)
    );

    // KOSDAQ 코드 매핑 (4자리)
    private static final Map<String, String> KOSDAQ_TO_GICS = Map.ofEntries(
        Map.entry("1009", "35"), // 헬스케어
        Map.entry("1014", "40"), // 금융 (지주회사/스팩)
        Map.entry("1015", "50"), // 통신서비스 (미디어)
        Map.entry("1010", "20"), // 산업재
        Map.entry("1013", "20")  // 산업재 (운송)
        // "1006": 매핑 불가 (부동산/통신/산업재 혼재)
        // "1011": 에너지 (주의: 임의소비재(한국가구) 혼재) - 일단 제외
    );

    // 해외 주식 코드 매핑 (3자리)
    private static final Map<String, String> OVERSEAS_TO_GICS = new HashMap<>();
    static {
        OVERSEAS_TO_GICS.put("530", "35"); // 헬스케어
        OVERSEAS_TO_GICS.put("610", "40"); // 금융
        OVERSEAS_TO_GICS.put("630", "40"); // 금융 (mREIT)
        OVERSEAS_TO_GICS.put("640", "40"); // 금융
        OVERSEAS_TO_GICS.put("10", "10");   // 에너지
        OVERSEAS_TO_GICS.put("720", "45"); // 정보기술
        OVERSEAS_TO_GICS.put("510", "35"); // 헬스케어
        OVERSEAS_TO_GICS.put("230", "20"); // 산업재
        OVERSEAS_TO_GICS.put("370", "50"); // 통신서비스 (미디어)
        OVERSEAS_TO_GICS.put("380", "25"); // 임의소비재
        OVERSEAS_TO_GICS.put("730", "45"); // 정보기술
        OVERSEAS_TO_GICS.put("350", "25"); // 임의소비재
        OVERSEAS_TO_GICS.put("410", "30"); // 필수소비재
        OVERSEAS_TO_GICS.put("130", "15"); // 소재
        OVERSEAS_TO_GICS.put("260", "20"); // 산업재
        OVERSEAS_TO_GICS.put("710", "45"); // 정보기술
        OVERSEAS_TO_GICS.put("520", "35"); // 헬스케어
        OVERSEAS_TO_GICS.put("620", "40"); // 금융
        OVERSEAS_TO_GICS.put("110", "15"); // 소재
        OVERSEAS_TO_GICS.put("220", "20"); // 산업재
        OVERSEAS_TO_GICS.put("740", "45"); // 정보기술
        OVERSEAS_TO_GICS.put("930", "55"); // 유틸리티
        OVERSEAS_TO_GICS.put("910", "55"); // 유틸리티
        OVERSEAS_TO_GICS.put("210", "20"); // 산업재
        OVERSEAS_TO_GICS.put("330", "25"); // 임의소비재
        OVERSEAS_TO_GICS.put("420", "30"); // 필수소비재
        OVERSEAS_TO_GICS.put("340", "25"); // 임의소비재
        OVERSEAS_TO_GICS.put("320", "25"); // 임의소비재
        OVERSEAS_TO_GICS.put("250", "20"); // 산업재
        OVERSEAS_TO_GICS.put("360", "25"); // 임의소비재 (서비스)
    }

    // 매핑 불가능한 코드 (혼합 바스켓)
    private static final Map<String, String> UNMAPPABLE_CODES = Map.ofEntries(
        Map.entry("1006", "KOSDAQ: 부동산/통신/산업재 혼재"),
        Map.entry("1011", "KOSDAQ: 에너지/임의소비재 혼재")
    );

    /**
     * KIS 업종 코드를 GICS Sector로 매핑합니다.
     * 
     * @param sectorCode KIS 업종 코드 (KOSPI: 2자리, KOSDAQ: 4자리, 해외: 3자리)
     * @param exchangeNum 거래소 (KOSPI, KOSDAQ, NAS, NYS, AMS 등)
     * @return 매핑된 GICS Sector, 매핑 불가능하거나 알 수 없으면 UNKNOWN
     */
    public static SECTOR mapToGicsSector(String sectorCode, EXCHANGENUM exchangeNum) {
        if (sectorCode == null || sectorCode.isEmpty() || sectorCode.equals("nan") || sectorCode.equals("0")) {
            return SECTOR.UNKNOWN;
        }

        String trimmedCode = sectorCode.trim();
        
        // 매핑 불가능한 코드 확인
        if (UNMAPPABLE_CODES.containsKey(trimmedCode)) {
            log.debug("Unmappable sector code: {} ({})", trimmedCode, UNMAPPABLE_CODES.get(trimmedCode));
            return SECTOR.UNKNOWN;
        }

        String gicsCode = null;

        // 거래소별 매핑
        if (exchangeNum == EXCHANGENUM.KOSPI) {
            gicsCode = KOSPI_TO_GICS.get(trimmedCode);
        } else if (exchangeNum == EXCHANGENUM.KOSDAQ) {
            gicsCode = KOSDAQ_TO_GICS.get(trimmedCode);
        } else if (isOverseasExchange(exchangeNum)) {
            // 해외 주식: 3자리 코드 그대로 사용
            gicsCode = OVERSEAS_TO_GICS.get(trimmedCode);
        }

        if (gicsCode == null) {
            log.debug("Unknown sector code: {} for exchange: {}", trimmedCode, exchangeNum);
            return SECTOR.UNKNOWN;
        }

        // GICS 코드를 SECTOR enum으로 변환
        try {
            return SECTOR.fromCode(gicsCode);
        } catch (Exception e) {
            log.warn("Failed to convert GICS code {} to SECTOR enum", gicsCode, e);
            return SECTOR.UNKNOWN;
        }
    }

    /**
     * 해외 거래소인지 확인합니다.
     */
    private static boolean isOverseasExchange(EXCHANGENUM exchangeNum) {
        return exchangeNum == EXCHANGENUM.NAS || 
               exchangeNum == EXCHANGENUM.NYS || 
               exchangeNum == EXCHANGENUM.AMS;
    }
}

