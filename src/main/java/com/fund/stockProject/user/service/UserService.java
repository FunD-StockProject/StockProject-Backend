package com.fund.stockProject.user.service;

import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.experiment.repository.ExperimentRepository;
import com.fund.stockProject.experiment.repository.ExperimentTradeItemRepository;
import com.fund.stockProject.global.service.S3Service;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.UserDeviceTokenRepository;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.user.dto.UserProfileResponse;
import com.fund.stockProject.user.dto.UserUpdateRequest;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final PreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentTradeItemRepository experimentTradeItemRepository;

    @Transactional
    public UserProfileResponse updateMyProfile(UserUpdateRequest request) {
        String email = AuthService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        user.updateProfile(request.getNickname(), request.getBirthDate(), request.getMarketingAgreement());
        userRepository.save(user);

        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getMarketingAgreement(),
                user.getProfileImageUrl()
        );
    }

    @Transactional
    public UserProfileResponse updateProfileImage(MultipartFile image) {
        String email = AuthService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        if (image != null && !image.isEmpty()) {
            String imageUrl = s3Service.uploadUserImage(image, "users");
            user.updateProfileImage(imageUrl);
            userRepository.save(user);
        }

        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getMarketingAgreement(),
                user.getProfileImageUrl()
        );
    }

    /**
     * 사용자의 모든 데이터를 삭제합니다.
     * 북마크, 알림, 실험, 포트폴리오 등 모든 사용자 관련 데이터를 삭제합니다.
     * 회원 탈퇴와 달리 사용자 계정은 유지됩니다.
     */
    @Transactional
    public void deleteAllUserData() {
        String email = AuthService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("Authentication required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        Integer userId = user.getId();

        // 1. ExperimentTradeItem 삭제 (외래 키 제약 조건 때문에 가장 먼저 삭제)
        experimentTradeItemRepository.deleteByUserId(userId);

        // 2. Experiment 삭제
        experimentRepository.deleteByUserId(userId);

        // 3. Preference 데이터 삭제 (북마크 포함)
        preferenceRepository.deleteByUserId(userId);

        // 4. Notification 데이터 삭제
        notificationRepository.deleteByUserId(userId);

        // 5. UserDeviceToken 데이터 삭제
        userDeviceTokenRepository.deleteByUserId(userId);
    }
}
