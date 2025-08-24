package com.fund.stockProject.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "디바이스 플랫폼 구분 값 (푸시 토큰 발급 주체)")
public enum DevicePlatform {
    @Schema(description = "안드로이드 (FCM)")
    ANDROID,
    @Schema(description = "iOS (APNs -> FCM 토큰 브릿지)")
    IOS,
    @Schema(description = "웹 (브라우저 WebPush/ServiceWorker)")
    WEB
}

