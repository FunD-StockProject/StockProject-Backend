package com.fund.stockProject.preference.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Schema(description = "종목 관심/알림 여부 조회 응답 DTO")
public class StockPreferenceResponse {
    @Schema(description = "종목 ID", example = "1")
    private final Integer stockId;

    @Schema(description = "관심 종목(북마크) 여부", example = "true")
    private final Boolean isBookmarked;

    @Schema(description = "알림 활성화 여부", example = "false")
    private final Boolean isNotificationEnabled;

    @Builder
    public StockPreferenceResponse(Integer stockId, Boolean isBookmarked, Boolean isNotificationEnabled) {
        this.stockId = stockId;
        this.isBookmarked = isBookmarked;
        this.isNotificationEnabled = isNotificationEnabled;
    }
}

