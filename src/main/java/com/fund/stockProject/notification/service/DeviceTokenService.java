package com.fund.stockProject.notification.service;

import com.fund.stockProject.notification.domain.DevicePlatform;
import com.fund.stockProject.notification.dto.RegisterDeviceTokenRequest;
import com.fund.stockProject.notification.dto.UnregisterDeviceTokenRequest;
import com.fund.stockProject.notification.entity.UserDeviceToken;
import com.fund.stockProject.notification.repository.UserDeviceTokenRepository;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserDeviceTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void registerToken(Integer userId, RegisterDeviceTokenRequest request) {
        String token = request.getToken() != null ? request.getToken().trim() : null;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }

        DevicePlatform platform = request.getPlatform();
        if (platform == null) {
            throw new IllegalArgumentException("Platform is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Defensive dedup: DB unique 제약이 누락/깨진 환경에서도 한 토큰은 한 사용자만 소유하도록 보정
        List<UserDeviceToken> duplicates = tokenRepository.findAllByToken(token);
        UserDeviceToken entity = duplicates.isEmpty()
                ? UserDeviceToken.builder().token(token).build()
                : duplicates.get(0);

        // 동일 사용자/플랫폼에서 기존 토큰은 비활성화하고 최신 토큰 1개만 유지
        tokenRepository.deactivateByUserIdAndPlatformExceptToken(userId, platform, token);

        entity.setUser(user);
        entity.setPlatform(platform);
        entity.setIsActive(true);
        tokenRepository.save(entity);

        if (duplicates.size() > 1) {
            for (int i = 1; i < duplicates.size(); i++) {
                UserDeviceToken duplicate = duplicates.get(i);
                duplicate.setIsActive(false);
                tokenRepository.save(duplicate);
            }
        }
    }

    @Transactional
    public void unregisterToken(Integer userId, UnregisterDeviceTokenRequest request) {
        String token = request.getToken() != null ? request.getToken().trim() : null;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }

        tokenRepository.deactivateByTokenAndUserId(token, userId);
        // idempotent: no error if not found or already inactive
    }

    @Transactional
    public void unregisterOnLogout(Integer userId, String token) {
        String normalized = token != null ? token.trim() : null;
        if (normalized == null || normalized.isBlank()) {
            // 멀티 디바이스 로그인 환경에서는 deviceToken 미전달 로그아웃 시
            // 다른 기기의 푸시 토큰까지 비활성화하면 안 되므로 no-op 처리
            return;
        }
        tokenRepository.deactivateByTokenAndUserId(normalized, userId);
    }
}
