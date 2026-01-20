package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.repository.UserDeviceTokenRepository;
import com.google.api.client.http.HttpResponseException;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushService {

    private final FirebaseMessaging firebaseMessaging;
    private final UserDeviceTokenRepository tokenRepo;

    /**
     * 일반 알림(표시형) – 알림센터에 뜸
     */
    public void sendAlert(Integer userId, String title, String body, Map<String, String> data) {
        sendInternal(userId, /*silent*/ false, title, body, data);
    }

    /**
     * 사일런트 알림(비표시형) – 백그라운드 데이터 갱신용
     */
    public void sendSilent(Integer userId, Map<String, String> data) {
        sendInternal(userId, /*silent*/ true, null, null, data);
    }

    private void sendInternal(Integer userId, boolean silent, String title, String body, Map<String, String> data) {
        List<String> tokens = tokenRepo.findActiveTokens(userId);
        if (tokens.isEmpty()) return;

        List<Message> messages = new ArrayList<>();
        for (String token : tokens) {
            try {
                Message.Builder b = Message.builder().setToken(token);

                // 공통 data
                if (data != null && !data.isEmpty()) b.putAllData(data);

                // Android 설정
                AndroidConfig.Builder android = AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH);
                if (!silent && title != null) {
                    android.setNotification(AndroidNotification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());
                }
                b.setAndroidConfig(android.build());

                // iOS(APNs) 설정 개선
                ApnsConfig.Builder apns = ApnsConfig.builder();
                var apsBuilder = Aps.builder();

                if (silent) {
                    // 사일런트 푸시를 위한 설정
                    apsBuilder.setContentAvailable(true);
                    // 사일런트 푸시에서는 badge, sound, alert 제거
                } else if (title != null) {
                    // 일반 알림을 위한 설정
                    apsBuilder.setAlert(ApsAlert.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                            .setSound("default")
                            .setBadge(1); // 배지 설정 추가
                }

                // APNs Headers 추가 (iOS 앱 번들 ID와 일치해야 함)
                apns.setAps(apsBuilder.build());

                // APNs topic 설정 (필수: 앱의 Bundle Identifier)
                apns.putHeader("apns-topic", "com.durumi99.humanzipyoapp");

                // APNs 우선순위 설정
                if (!silent) {
                    apns.putHeader("apns-priority", "10"); // 높은 우선순위
                } else {
                    apns.putHeader("apns-priority", "5"); // 낮은 우선순위 (사일런트)
                }

                // APNs 만료 시간 설정 (1시간)
                apns.putHeader("apns-expiration", String.valueOf(System.currentTimeMillis() / 1000 + 3600));

                b.setApnsConfig(apns.build());
                messages.add(b.build());

            } catch (Exception e) {
                log.warn("Failed to build message for token={}, error={}", token, e.getMessage());
                // 메시지 빌드 실패한 토큰은 건너뛰기
                continue;
            }
        }

        if (messages.isEmpty()) {
            log.warn("No valid messages to send for userId={}", userId);
            return;
        }

        // 대량 전송(batch)
        try {
            BatchResponse resp = firebaseMessaging.sendEach(messages);
            log.info("FCM batch send completed userId={}, success={}, failure={}",
                    userId, resp.getSuccessCount(), resp.getFailureCount());

            // 결과 분석: 실패 토큰 정리
            for (int i = 0; i < resp.getResponses().size(); i++) {
                SendResponse r = resp.getResponses().get(i);
                if (!r.isSuccessful()) {
                    var ex = r.getException();
                    handleSendFailure(tokens.get(i), ex);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM batch send failed userId={}, err={}, detail={}", userId, e.getMessage(), buildDetail(e));
        }
    }

    private void handleSendFailure(String token, FirebaseMessagingException ex) {
        String code = String.valueOf(ex.getErrorCode());

        // APNs 관련 오류 처리 강화
        if ("THIRD_PARTY_AUTH_ERROR".equalsIgnoreCase(code) ||
            "UNAUTHENTICATED".equalsIgnoreCase(code)) {

            // APNs 인증 오류 - 토큰 비활성화하지 않고 로그만 남김
            log.error("APNs authentication error for token={}, code={}, detail={}",
                     token, code, buildDetail(ex));

            // APNs 설정 문제일 가능성이 높으므로 토큰은 유지
            return;
        }

        // 대표적인 만료/폐기 케이스
        if ("UNREGISTERED".equalsIgnoreCase(code)
                || "INVALID_ARGUMENT".equalsIgnoreCase(code)
                || "SENDER_ID_MISMATCH".equalsIgnoreCase(code)
                || "INVALID_REGISTRATION".equalsIgnoreCase(code)) {
            tokenRepo.findByToken(token).ifPresent(t -> {
                t.setIsActive(false);
                tokenRepo.save(t);
            });
            log.info("Deactivated invalid token={}, code={}, detail={}", token, code, buildDetail(ex));
        } else {
            log.warn("Send failed token={}, code={}, msg={}, detail={}",
                    token, code, ex.getMessage(), buildDetail(ex));
        }
    }

    /**
     * FirebaseMessagingException 에서 가능한 한 많은 진단 정보를 문자열로 구성.
     */
    private String buildDetail(FirebaseMessagingException ex) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("errorCode=").append(ex.getErrorCode());

            // messagingErrorCode (신버전 SDK 에 있을 수 있음)
            try {
                var m = ex.getClass().getMethod("getMessagingErrorCode");
                Object mec = m.invoke(ex);
                if (mec != null) sb.append(", messagingErrorCode=").append(mec);
            } catch (Exception ignore) {}

            // HttpResponse (내부) 추출
            try {
                var mResp = ex.getClass().getMethod("getHttpResponse");
                Object resp = mResp.invoke(ex);
                if (resp != null) {
                    try {
                        var mStatus = resp.getClass().getMethod("getStatusCode");
                        Object sc = mStatus.invoke(resp);
                        sb.append(", httpStatus=").append(sc);
                    } catch (Exception ignore) {}

                    // parseAsString 시도
                    try {
                        var mParse = resp.getClass().getMethod("parseAsString");
                        Object bodyStr = mParse.invoke(resp);
                        if (bodyStr != null) {
                            String s = bodyStr.toString();
                            if (s.length() > 400) s = s.substring(0, 400) + "...";
                            sb.append(", httpBody=").append(s.replaceAll("\n", " "));
                        }
                    } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {}

            // cause 가 HttpResponseException 인 경우 직접 content 추출
            if (ex.getCause() instanceof HttpResponseException hre) {
                sb.append(", status=").append(hre.getStatusCode())
                  .append(", statusMsg=").append(hre.getStatusMessage());
                String content = hre.getContent();
                if (content != null && !content.isBlank()) {
                    String c = content.trim();
                    if (c.length() > 400) c = c.substring(0, 400) + "...";
                    sb.append(", httpContent=").append(c.replaceAll("\n", " "));
                }
            }

            if (ex.getCause() != null) {
                sb.append(", cause=").append(ex.getCause().getClass().getSimpleName())
                  .append(":").append(ex.getCause().getMessage());
            }
        } catch (Exception reflectionErr) {
            sb.append("(detail-extract-failed:").append(reflectionErr.getClass().getSimpleName()).append(")");
        }
        return sb.toString();
    }

    /**
     * 토큰 유효성 간단 검증 (dry-run 방식)
     */
    public boolean isValidToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            // 최소한의 테스트 메시지로 토큰 형식 검증
            Message testMessage = Message.builder()
                    .setToken(token)
                    .putData("validation", "test")
                    .build();

            // dry-run으로 실제 전송 없이 검증만 수행
            firebaseMessaging.send(testMessage, true);
            return true;
        } catch (FirebaseMessagingException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().name() : "UNKNOWN";

            // INVALID_ARGUMENT는 토큰 형식 문제이므로 false 반환
            if ("INVALID_ARGUMENT".equals(errorCode) || "UNREGISTERED".equals(errorCode)) {
                return false;
            }

            // THIRD_PARTY_AUTH_ERROR나 UNAUTHENTICATED는 서버 설정 문제이므로
            // 토큰 자체는 유효할 수 있음
            if ("THIRD_PARTY_AUTH_ERROR".equals(errorCode) || "UNAUTHENTICATED".equals(errorCode)) {
                log.warn("FCM server configuration issue detected: {}", errorCode);
                return true; // 토큰 형식은 유효하다고 가정
            }

            log.debug("Token validation failed: token={}, error={}", token, e.getMessage());
            return false;
        }
    }
}
