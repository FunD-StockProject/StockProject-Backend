package com.fund.stockProject.notification.dto;

import com.fund.stockProject.notification.domain.NotificationType;
import com.fund.stockProject.notification.entity.Notification;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Schema(description = "알림 응답 DTO: 점수 변동 등 사용자에게 전달되는 알림 정보")
public class NotificationResponse {
    @Schema(description = "알림 ID", example = "101")
    private Integer id;
    @Schema(description = "관련 종목 ID", example = "1234")
    private Integer stockId;
    @Schema(description = "관련 종목 심볼/이름 (없을 수 있음)", example = "AAPL")
    private String stockName;
    @Schema(description = "알림 타입", example = "SCORE_SPIKE")
    private NotificationType notificationType;
    @Schema(description = "변경 전 점수", example = "75")
    private Integer oldScore;
    @Schema(description = "변경 후 점수", example = "83")
    private Integer newScore;
    @Schema(description = "점수 절대 변화량", example = "8")
    private Integer changeAbs;
    @Schema(description = "알림 제목", example = "점수 급등 알림")
    private String title;
    @Schema(description = "알림 본문", example = "AAPL 점수가 75 -> 83(+8) 상승")
    private String body;
    @Schema(description = "읽음 여부", example = "false")
    private Boolean isRead;
    @Schema(description = "알림 생성 시각 (UTC ISO-8601)", example = "2025-08-24T12:34:56Z", type = "string", format = "date-time")
    private Instant createdAt;
    
    @Schema(description = "종목 국가 정보 (KOREA 또는 OVERSEA)", example = "KOREA")
    private COUNTRY country;

    public static NotificationResponse fromEntity(Notification notification) {
        COUNTRY country = null;
        if (notification.getStock() != null && notification.getStock().getExchangeNum() != null) {
            country = getCountryFromExchangeNum(notification.getStock().getExchangeNum());
        }
        
        return NotificationResponse.builder()
                .id(notification.getId())
                .stockId(notification.getStock() != null ? notification.getStock().getId() : null)
                .stockName(notification.getStock() != null ? notification.getStock().getSymbolName() : null)
                .notificationType(notification.getNotificationType())
                .oldScore(notification.getOldScore())
                .newScore(notification.getNewScore())
                .changeAbs(notification.getChangeAbs())
                .title(notification.getTitle())
                .body(notification.getBody())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .country(country)
                .build();
    }
    
    private static COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangeNum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
            .contains(exchangeNum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }
}
