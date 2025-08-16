package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.global.service.S3Service;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static com.fund.stockProject.auth.domain.ROLE.ROLE_USER;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
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
        userRepository.deleteUserByEmail(email);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public String register(OAuth2RegisterRequest oAuth2RegisterRequest) {
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
    }
}
