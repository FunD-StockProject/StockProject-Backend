package com.fund.stockProject.stock.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 섹터 분류
 * 이미 구해놓은 섹터 정보를 위한 도메인 클래스
 */
@Getter
@RequiredArgsConstructor
public enum SECTOR {

    // 에너지 (Energy)
    ENERGY("10", "Energy"),

    // 소재 (Materials)
    MATERIALS("15", "Materials"),

    // 산업재 (Industrials)
    INDUSTRIALS("20", "Industrials"),

    // 필수소비재 (Consumer Discretionary)
    CONSUMER_DISCRETIONARY("25", "Consumer Discretionary"),

    // 필수소비재 (Consumer Staples)
    CONSUMER_STAPLES("30", "Consumer Staples"),

    // 헬스케어 (Health Care)
    HEALTH_CARE("35", "Health Care"),

    // 금융 (Financials)
    FINANCIALS("40", "Financials"),

    // 정보기술 (Information Technology)
    INFORMATION_TECHNOLOGY("45", "Information Technology"),

    // 통신서비스 (Communication Services)
    COMMUNICATION_SERVICES("50", "Communication Services"),

    // 유틸리티 (Utilities)
    UTILITIES("55", "Utilities"),

    // 부동산 (Real Estate)
    REAL_ESTATE("60", "Real Estate"),

    // 기타 (Unknown)
    UNKNOWN("99", "Unknown");

    private final String code;
    private final String name;

    /**
     * 코드로 섹터를 찾습니다.
     * 2자리 코드 또는 GICS 코드(4자리 이상)를 지원합니다.
     * GICS 코드의 경우 첫 2자리만 추출하여 매핑합니다.
     * 
     * @param code 섹터 코드 (예: "10", "101010", "15", "151010")
     * @return 매핑된 SECTOR, 없으면 UNKNOWN
     */
    public static SECTOR fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        
        // GICS 코드인 경우 첫 2자리만 추출 (4자리 이상인 경우)
        String sectorCode = code;
        if (code.length() >= 2) {
            sectorCode = code.substring(0, 2);
        }
        
        // 2자리 코드로 매핑 시도
        for (SECTOR sector : values()) {
            if (sector.getCode().equals(sectorCode)) {
                return sector;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * GICS 코드로 섹터를 찾습니다.
     * GICS 코드의 첫 2자리를 추출하여 Sector로 매핑합니다.
     * 
     * @param gicsCode GICS 코드 (예: "101010", "151010", "201010")
     * @return 매핑된 SECTOR, 없으면 UNKNOWN
     */
    public static SECTOR fromGicsCode(String gicsCode) {
        return fromCode(gicsCode);
    }

    /**
     * 이름으로 섹터를 찾습니다.
     */
    public static SECTOR fromName(String name) {
        for (SECTOR sector : values()) {
            if (sector.getName().equalsIgnoreCase(name)) {
                return sector;
            }
        }
        return UNKNOWN;
    }
}