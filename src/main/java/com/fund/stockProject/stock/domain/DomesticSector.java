package com.fund.stockProject.stock.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 국내 주식 섹터 분류 (KOSPI/KOSDAQ 통합)
 * 한국투자증권(KIS)에서 제공하는 업종 코드를 기반으로 합니다.
 * 업종명을 기준으로 KOSPI와 KOSDAQ을 통합하여 관리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum DomesticSector {

    // 유통 (KOSPI: 0016, KOSDAQ: 1011)
    RETAIL("유통", "0016", "1011"),

    // 건설 (KOSPI: 0018, KOSDAQ: 1010)
    CONSTRUCTION("건설", "0018", "1010"),

    // 운송·창고 (KOSPI: 0019, KOSDAQ: 1013)
    TRANSPORTATION_WAREHOUSING("운송·창고", "0019", "1013"),

    // 금융 (KOSPI: 0021, KOSDAQ: 1014)
    FINANCIAL("금융", "0021", "1014"),

    // 일반서비스 (KOSPI: 0026, KOSDAQ: 1006)
    GENERAL_SERVICE("일반서비스", "0026", "1006"),

    // 제조 (KOSPI: 0027, KOSDAQ: 1009)
    MANUFACTURING("제조", "0027", "1009"),

    // 오락·문화 (KOSPI: 0030, KOSDAQ: 1015)
    ENTERTAINMENT_CULTURE("오락·문화", "0030", "1015"),

    // 전기·가스 (KOSPI: 0017만)
    UTILITIES("전기·가스", "0017", null),

    // 통신 (KOSPI: 0020만)
    TELECOMMUNICATIONS("통신", "0020", null),

    // 부동산 (KOSPI: 0028만)
    REAL_ESTATE("부동산", "0028", null),

    // IT 서비스 (KOSPI: 0029만)
    IT_SERVICE("IT 서비스", "0029", null),

    // 기타 (Unknown)
    UNKNOWN("미분류", null, null);

    private final String name;           // 업종명
    private final String kospiCode;      // KOSPI 업종 코드 (4자리, 실제로는 마지막 2자리 사용)
    private final String kosdaqCode;      // KOSDAQ 업종 코드 (4자리)

    // KOSPI 코드 -> DomesticSector 매핑
    private static final Map<String, DomesticSector> KOSPI_CODE_MAP = new HashMap<>();
    // KOSDAQ 코드 -> DomesticSector 매핑
    private static final Map<String, DomesticSector> KOSDAQ_CODE_MAP = new HashMap<>();

    static {
        for (DomesticSector sector : values()) {
            if (sector.kospiCode != null) {
                // KOSPI 코드는 4자리이지만 실제로는 마지막 2자리만 사용 (예: 0016 -> 16)
                String kospiShortCode = sector.kospiCode.substring(2);
                KOSPI_CODE_MAP.put(sector.kospiCode, sector);
                KOSPI_CODE_MAP.put(kospiShortCode, sector);
            }
            if (sector.kosdaqCode != null) {
                KOSDAQ_CODE_MAP.put(sector.kosdaqCode, sector);
            }
        }
    }

    /**
     * KOSPI 업종 코드로 섹터를 찾습니다.
     * 
     * @param code KOSPI 업종 코드 (2자리 또는 4자리, 예: "16", "0016")
     * @return 매핑된 DomesticSector, 없으면 UNKNOWN
     */
    public static DomesticSector fromKospiCode(String code) {
        if (code == null || code.isEmpty() || code.equals("0") || code.equals("0000")) {
            return UNKNOWN;
        }

        String trimmedCode = code.trim();
        
        // 2자리 코드를 4자리로 변환 (예: "16" -> "0016")
        if (trimmedCode.length() == 2) {
            trimmedCode = "00" + trimmedCode;
        }

        return KOSPI_CODE_MAP.getOrDefault(trimmedCode, UNKNOWN);
    }

    /**
     * KOSDAQ 업종 코드로 섹터를 찾습니다.
     * 
     * @param code KOSDAQ 업종 코드 (4자리, 예: "1011")
     * @return 매핑된 DomesticSector, 없으면 UNKNOWN
     */
    public static DomesticSector fromKosdaqCode(String code) {
        if (code == null || code.isEmpty() || code.equals("0") || code.equals("0000")) {
            return UNKNOWN;
        }

        String trimmedCode = code.trim();
        return KOSDAQ_CODE_MAP.getOrDefault(trimmedCode, UNKNOWN);
    }

    /**
     * 거래소와 코드로 섹터를 찾습니다.
     * 
     * @param code 업종 코드
     * @param exchangeNum 거래소 (KOSPI 또는 KOSDAQ)
     * @return 매핑된 DomesticSector, 없으면 UNKNOWN
     */
    public static DomesticSector fromCode(String code, EXCHANGENUM exchangeNum) {
        if (code == null || code.isEmpty() || code.equals("0") || code.equals("0000") || code.equals("nan")) {
            return UNKNOWN;
        }

        if (exchangeNum == EXCHANGENUM.KOSPI) {
            return fromKospiCode(code);
        } else if (exchangeNum == EXCHANGENUM.KOSDAQ) {
            return fromKosdaqCode(code);
        }

        return UNKNOWN;
    }

    /**
     * 업종명으로 섹터를 찾습니다.
     * 
     * @param name 업종명 (예: "유통", "건설")
     * @return 매핑된 DomesticSector, 없으면 UNKNOWN
     */
    public static DomesticSector fromName(String name) {
        if (name == null || name.isEmpty()) {
            return UNKNOWN;
        }

        String normalizedName = name.trim();
        // 숫자 제거 (예: "16유통" -> "유통")
        normalizedName = normalizedName.replaceAll("^\\d+", "").trim();

        for (DomesticSector sector : values()) {
            if (sector.name.equals(normalizedName)) {
                return sector;
            }
        }

        return UNKNOWN;
    }
}

