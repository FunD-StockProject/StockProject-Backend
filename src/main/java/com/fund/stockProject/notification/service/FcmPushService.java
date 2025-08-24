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
            // 네트워크/일시적 오류 – Outbox 레이어에서 재시도하므로 여기선 로그만
            log.warn("FCM batch send failed userId={}, err={}", userId, e.getMessage());
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
            log.info("deactivated invalid token={}", token);
        } else {
            log.warn("send fail token={}, code={}, msg={}", token, code, ex.getMessage());
        }
    }
}
