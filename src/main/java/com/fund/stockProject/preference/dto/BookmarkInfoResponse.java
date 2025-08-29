package com.fund.stockProject.preference.dto;

import com.fund.stockProject.stock.domain.COUNTRY;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Schema(description = "사용자 북마크 종목 정보 응답 DTO")
public class BookmarkInfoResponse {
    @Schema(description = "종목 ID", example = "1")
    private final Integer stockId;

    @Schema(description = "종목 이름", example = "테슬라")
    private final String name;

    @Schema(description = "현재 가격", example = "20300")
    private final Integer price;

    @Schema(description = "전일 대비 등락률(%)", example = "-2.91")
    private final Double priceDiffPerCent;

    @Schema(description = "현재 점수", example = "46")
    private final Integer score;

    @Schema(description = "점수 변동값 (이전 대비)", example = "23")
    private final Integer diff;

    @Schema(description = "알림 활성화 여부", example = "false")
    private final Boolean isNotificationOn;

    @Schema(description = "종목 심볼", example = "TSLA")
    private final String symbolName;

    @Schema(description = "국가 구분", example = "OVERSEA")
    private final COUNTRY country;

    @Builder
    public BookmarkInfoResponse(Integer stockId, String name, Integer price, Double priceDiffPerCent, 
                               Integer score, Integer diff, Boolean isNotificationOn, 
                               String symbolName, COUNTRY country) {
        this.stockId = stockId;
        this.name = name;
        this.price = price;
        this.priceDiffPerCent = priceDiffPerCent;
        this.score = score;
        this.diff = diff;
        this.isNotificationOn = isNotificationOn;
        this.symbolName = symbolName;
        this.country = country;
    }
}
