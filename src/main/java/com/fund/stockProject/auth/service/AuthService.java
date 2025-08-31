package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.notification.repository.NotificationRepository;
import com.fund.stockProject.notification.repository.UserDeviceTokenRepository;
import com.fund.stockProject.auth.repository.RefreshTokenRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.global.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.fund.stockProject.auth.domain.ROLE.ROLE_USER;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final S3Service s3Service;

    /**
     * 현재 사용자가 인증된 상태인지 확인합니다.
     * @return 인증된 경우 true, 그렇지 않은 경우 false
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    // getCurrentUser 만들려고 했으나 static 이슈로 안 만듦

    /**
     * 현재 인증된 사용자의 ID를 반환합니다.
     * 인증되지 않은 경우 null을 반환합니다.
     */
    public static String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!isAuthenticated()) {
            return null;
        }
        // TODO: UserPrincipal이 아닌 CustomUserDetails를 사용하도록 변경
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        return principal.getEmail();
    }

    @Transactional
    public void withdrawUser(String email) {
        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        Integer userId = user.getId();

        // 1. Preference 데이터 삭제 (외래 키 제약 조건 때문에 가장 먼저 삭제)
        preferenceRepository.deleteByUserId(userId);

        // 2. Notification 데이터 삭제
        notificationRepository.deleteByUserId(userId);

        // 3. UserDeviceToken 데이터 삭제
        userDeviceTokenRepository.deleteByUserId(userId);

        // 4. RefreshToken 데이터 삭제 (email 기반)
        refreshTokenRepository.deleteByEmail(email);

        // 5. 마지막으로 User 삭제
        userRepository.delete(user);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional(rollbackFor = Exception.class)
    public String register(OAuth2RegisterRequest oAuth2RegisterRequest) {
        try {
            MultipartFile image = oAuth2RegisterRequest.getImage();
            String imageUrl = (image != null && !image.isEmpty()) ? s3Service.uploadUserImage(image, "users") : null;

            User user = User.builder()
                    .email(oAuth2RegisterRequest.getEmail())
                    .nickname(oAuth2RegisterRequest.getNickname())
                    .birthDate(oAuth2RegisterRequest.getBirthDate())
                    .provider(oAuth2RegisterRequest.getProvider())
                    .role(ROLE_USER)
                    .isActive(true)
                    .marketingAgreement(oAuth2RegisterRequest.getMarketingAgreement())
                    .profileImageUrl(imageUrl)
                    .build();

            userRepository.save(user);
            return imageUrl;
        } catch (Exception e) {
            // 로그 추가
            System.err.println("회원가입 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
