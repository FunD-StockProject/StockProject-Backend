package com.fund.stockProject.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 타입 Enum. 시스템이 사용자에게 전달할 수 있는 알림 분류")
public enum NotificationType {
    @Schema(description = "점수 급변 (스파이크) 발생 시 전달되는 알림")
    SCORE_SPIKE("점수 급변 알림");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
