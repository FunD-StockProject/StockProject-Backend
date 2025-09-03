package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.repository.UserDeviceTokenRepository;
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

            // iOS(APNs) 설정
            ApnsConfig.Builder apns = ApnsConfig.builder();
            var apsBuilder = Aps.builder();
            if (silent) {
                apsBuilder.setContentAvailable(true); // 사일런트
            } else if (title != null) {
                apsBuilder.setAlert(ApsAlert.builder().setTitle(title).setBody(body).build());
                apsBuilder.setSound("default");
            }
            apns.setAps(apsBuilder.build());
            b.setApnsConfig(apns.build());

            messages.add(b.build());
        }

        // 대량 전송(batch)
        try {
            BatchResponse resp = firebaseMessaging.sendEach(messages);
            // 결과 분석: 실패 토큰 정리
            for (int i = 0; i < resp.getResponses().size(); i++) {
                SendResponse r = resp.getResponses().get(i);
                if (!r.isSuccessful()) {
                    var ex = r.getException();
                    handleSendFailure(tokens.get(i), ex);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.warn("FCM batch send failed userId={}, err={}, detail={}", userId, e.getMessage(), buildDetail(e));
        }
    }

    private void handleSendFailure(String token, FirebaseMessagingException ex) {
        String code = String.valueOf(ex.getErrorCode());
        // 대표적인 만료/폐기 케이스
        if ("UNREGISTERED".equalsIgnoreCase(code)
                || "INVALID_ARGUMENT".equalsIgnoreCase(code)
                || "SENDER_ID_MISMATCH".equalsIgnoreCase(code)) {
            tokenRepo.findByToken(token).ifPresent(t -> {
                t.setIsActive(false);
                tokenRepo.save(t);
            });
            log.info("deactivated invalid token={} detail={}", token, buildDetail(ex));
        } else if ("UNAUTHENTICATED".equalsIgnoreCase(code)) {
            // 인증 자체 문제: 서비스 계정/프로젝트/권한/시계 불일치 가능
            log.warn("send fail (auth) token={}, code={}, detail={}", token, code, buildDetail(ex));
        } else {
            log.warn("send fail token={}, code={}, msg={}, detail={}", token, code, ex.getMessage(), buildDetail(ex));
        }
    }

    /**
     * FirebaseMessagingException 에서 가능한 한 많은 진단 정보를 문자열로 구성.
     */
    private String buildDetail(FirebaseMessagingException ex) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("errorCode=").append(ex.getErrorCode());
            try { // MessagingErrorCode (enum) 존재 시
                var m = ex.getClass().getMethod("getMessagingErrorCode");
                Object mec = m.invoke(ex);
                if (mec != null) sb.append(", messagingErrorCode=").append(mec);
            } catch (NoSuchMethodException ignore) {}
            // HTTP status/code 추출 (내부 구현 반영: getHttpResponse -> getStatusCode())
            try {
                var mResp = ex.getClass().getMethod("getHttpResponse");
                Object resp = mResp.invoke(ex);
                if (resp != null) {
                    try {
                        var mStatus = resp.getClass().getMethod("getStatusCode");
                        Object sc = mStatus.invoke(resp);
                        sb.append(", httpStatus=").append(sc);
                    } catch (NoSuchMethodException ignore) {}
                    try {
                        var mBody = resp.getClass().getMethod("getContent");
                        Object body = mBody.invoke(resp);
                        if (body != null) {
                            String bodyStr = body.toString();
                            if (bodyStr.length() > 400) bodyStr = bodyStr.substring(0, 400) + "..."; // 과다 방지
                            sb.append(", httpBody=").append(bodyStr.replaceAll("\n", " "));
                        }
                    } catch (NoSuchMethodException ignore) {}
                }
            } catch (NoSuchMethodException ignore) {}
            if (ex.getCause() != null) {
                sb.append(", cause=").append(ex.getCause().getClass().getSimpleName())
                  .append(":" ).append(ex.getCause().getMessage());
            }
        } catch (Exception reflectionErr) {
            sb.append("(detail-extract-failed:" ).append(reflectionErr.getClass().getSimpleName()).append(")");
        }
        return sb.toString();
    }
}
