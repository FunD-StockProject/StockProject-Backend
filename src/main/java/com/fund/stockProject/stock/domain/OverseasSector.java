package com.fund.stockProject.stock.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 해외 주식 섹터 분류
 * 한국투자증권(KIS)에서 제공하는 GICS 업종분류코드(3자리)를 기반으로 합니다.
 */
@Getter
@RequiredArgsConstructor
public enum OverseasSector {

    // 에너지
    ENERGY("10", "Energy"),

    // 소재
    MATERIALS("110", "Materials"),
    MATERIALS_2("130", "Materials"),

    // 산업재
    INDUSTRIALS("210", "Industrials"),
    INDUSTRIALS_2("220", "Industrials"),
    INDUSTRIALS_3("230", "Industrials"),
    INDUSTRIALS_4("250", "Industrials"),
    INDUSTRIALS_5("260", "Industrials"),

    // 임의소비재
    CONSUMER_DISCRETIONARY("320", "Consumer Discretionary"),
    CONSUMER_DISCRETIONARY_2("330", "Consumer Discretionary"),
    CONSUMER_DISCRETIONARY_3("340", "Consumer Discretionary"),
    CONSUMER_DISCRETIONARY_4("350", "Consumer Discretionary"),
    CONSUMER_DISCRETIONARY_5("360", "Consumer Discretionary"),
    CONSUMER_DISCRETIONARY_6("380", "Consumer Discretionary"),

    // 필수소비재
    CONSUMER_STAPLES("410", "Consumer Staples"),
    CONSUMER_STAPLES_2("420", "Consumer Staples"),

    // 헬스케어
    HEALTH_CARE("510", "Health Care"),
    HEALTH_CARE_2("520", "Health Care"),
    HEALTH_CARE_3("530", "Health Care"),

    // 금융
    FINANCIALS("610", "Financials"),
    FINANCIALS_2("620", "Financials"),
    FINANCIALS_3("630", "Financials"),
    FINANCIALS_4("640", "Financials"),

    // 정보기술
    INFORMATION_TECHNOLOGY("710", "Information Technology"),
    INFORMATION_TECHNOLOGY_2("720", "Information Technology"),
    INFORMATION_TECHNOLOGY_3("730", "Information Technology"),
    INFORMATION_TECHNOLOGY_4("740", "Information Technology"),

    // 통신서비스
    COMMUNICATION_SERVICES("370", "Communication Services"),

    // 유틸리티
    UTILITIES("910", "Utilities"),
    UTILITIES_2("930", "Utilities"),

    // 기타
    UNKNOWN("999", "Unknown");

    private final String code;    // GICS 업종분류코드 (3자리)
    private final String name;    // 섹터명

    // 코드 -> OverseasSector 매핑
    private static final Map<String, OverseasSector> CODE_MAP = new HashMap<>();

    static {
        for (OverseasSector sector : values()) {
            CODE_MAP.put(sector.code, sector);
        }
    }

    /**
     * GICS 업종분류코드로 섹터를 찾습니다.
     * 
     * @param code GICS 업종분류코드 (3자리, 예: "610", "720")
     * @return 매핑된 OverseasSector, 없으면 UNKNOWN
     */
    public static OverseasSector fromCode(String code) {
        if (code == null || code.isEmpty() || code.equals("0") || code.equals("000") || code.equals("nan")) {
            return UNKNOWN;
        }

        String trimmedCode = code.trim();
        
        // 3자리 코드로 정규화 (앞에 0이 있으면 제거)
        if (trimmedCode.length() > 3) {
            // 4자리 이상인 경우 앞부분만 사용 (예: "1010" -> "101")
            trimmedCode = trimmedCode.substring(0, 3);
        } else if (trimmedCode.length() < 3) {
            // 2자리 이하인 경우 앞에 0 추가 (예: "10" -> "010")
            trimmedCode = String.format("%3s", trimmedCode).replace(' ', '0');
        }

        return CODE_MAP.getOrDefault(trimmedCode, UNKNOWN);
    }

    /**
     * 섹터명으로 섹터를 찾습니다.
     * 
     * @param name 섹터명 (예: "Energy", "Financials")
     * @return 매핑된 OverseasSector, 없으면 UNKNOWN
     */
    public static OverseasSector fromName(String name) {
        if (name == null || name.isEmpty()) {
            return UNKNOWN;
        }

        String normalizedName = name.trim();
        for (OverseasSector sector : values()) {
            if (sector.name.equalsIgnoreCase(normalizedName)) {
                return sector;
            }
        }

        return UNKNOWN;
    }

    /**
     * 섹터의 대분류명을 반환합니다.
     * 같은 대분류에 속하는 여러 코드를 하나의 이름으로 통합합니다.
     * 
     * @return 대분류명 (예: "Energy", "Materials", "Industrials")
     */
    public String getCategoryName() {
        return this.name;
    }
}

