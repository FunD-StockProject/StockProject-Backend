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
import com.fund.stockProject.auth.domain.PROVIDER;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

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

    /**
     * 일반 회원가입
     */
    @Transactional(rollbackFor = Exception.class)
    public String registerLocal(LocalRegisterRequest request) {
        // 이메일 중복 확인
        if (isEmailDuplicate(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        // 닉네임 중복 확인
        if (isNicknameDuplicate(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.getNickname());
        }

        // 프로필 이미지 업로드
        MultipartFile image = request.getImage();
        String imageUrl = (image != null && !image.isEmpty()) ? s3Service.uploadUserImage(image, "users") : null;

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 사용자 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .birthDate(request.getBirthDate())
                .provider(PROVIDER.LOCAL)
                .role(ROLE_USER)
                .isActive(true)
                .marketingAgreement(request.getMarketingAgreement() != null ? request.getMarketingAgreement() : false)
                .profileImageUrl(imageUrl)
                .build();

        userRepository.save(user);
        return imageUrl;
    }

    /**
     * 일반 로그인
     */
    @Transactional
    public LoginResponse loginLocal(LocalLoginRequest request) {
        try {
            // 인증 시도
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // 인증 성공 시 사용자 정보 가져오기
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // LOCAL provider가 아니면 예외 발생
            if (user.getProvider() != PROVIDER.LOCAL) {
                throw new BadCredentialsException("일반 로그인은 일반 회원가입 사용자만 가능합니다");
            }

            // 토큰 발급
            return tokenService.issueTokensOnLogin(user.getEmail(), user.getRole(), null);

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다", e);
        }
    }
}
