package com.fund.stockProject.stock.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 해외 주식 섹터 분류
 * 한국투자증권(KIS)에서 제공하는 GICS 업종분류코드(3자리)를 기반으로 합니다.
 * 같은 섹터명에 속하는 여러 GICS 코드를 하나의 enum으로 통합 관리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum OverseasSector {

    // 에너지
    ENERGY("Energy", "10"),

    // 소재
    MATERIALS("Materials", "110", "130"),

    // 산업재
    INDUSTRIALS("Industrials", "210", "220", "230", "250", "260"),

    // 임의소비재
    CONSUMER_DISCRETIONARY("Consumer Discretionary", "320", "330", "340", "350", "360", "380"),

    // 필수소비재
    CONSUMER_STAPLES("Consumer Staples", "410", "420"),

    // 헬스케어
    HEALTH_CARE("Health Care", "510", "520", "530"),

    // 금융
    FINANCIALS("Financials", "610", "620", "630", "640"),

    // 정보기술
    INFORMATION_TECHNOLOGY("Information Technology", "710", "720", "730", "740"),

    // 통신서비스
    COMMUNICATION_SERVICES("Communication Services", "370"),

    // 유틸리티
    UTILITIES("Utilities", "910", "930"),

    // 기타
    UNKNOWN("Unknown", "999");

    private final String name;    // 섹터명
    private final List<String> codes;    // GICS 업종분류코드 리스트 (3자리)

    OverseasSector(String name, String... codes) {
        this.name = name;
        this.codes = Arrays.asList(codes);
    }

    /**
     * 첫 번째 코드를 반환합니다 (하위 호환성 유지)
     */
    public String getCode() {
        return codes.isEmpty() ? null : codes.get(0);
    }

    // 코드 -> OverseasSector 매핑
    private static final Map<String, OverseasSector> CODE_MAP = new HashMap<>();

    static {
        for (OverseasSector sector : values()) {
            for (String code : sector.codes) {
                CODE_MAP.put(code, sector);
            }
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

