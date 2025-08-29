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

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserDeviceTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void registerToken(Integer userId, RegisterDeviceTokenRequest request) {
        String token = request.getToken();
        DevicePlatform platform = request.getPlatform();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Upsert by unique token
        UserDeviceToken entity = tokenRepository.findByToken(token)
                .orElseGet(() -> UserDeviceToken.builder().token(token).build());

        entity.setUser(user);
        entity.setPlatform(platform);
        entity.setIsActive(true);

        tokenRepository.save(entity);
    }

    @Transactional
    public void unregisterToken(Integer userId, UnregisterDeviceTokenRequest request) {
        String token = request.getToken();

        tokenRepository.findByTokenAndUserId(token, userId)
                .ifPresent(entity -> {
                    entity.setIsActive(false);
                    tokenRepository.save(entity);
                });
        // idempotent: no error if not found or already inactive
    }
}
