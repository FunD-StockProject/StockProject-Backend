package com.fund.stockProject.notification.domain;

public enum NotificationType {
    SCORE_SPIKE("점수 급변 알림"),
    SUBSCRIPTION_STARTED("구독 시작 알림"),
    SUBSCRIPTION_STOPPED("구독 해제 알림"),
    DAILY_SUMMARY("일일 요약 알림"),
    MARKET_OPEN("장 시작 알림"),
    MARKET_CLOSE("장 마감 알림"),
    SYSTEM_MAINTENANCE("시스템 점검 알림");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
