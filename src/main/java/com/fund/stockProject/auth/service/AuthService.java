package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.auth.entity.PasswordResetToken;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.PasswordResetTokenRepository;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.email.EmailService;
import com.fund.stockProject.security.principle.CustomPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.fund.stockProject.auth.domain.ROLE.ROLE_USER;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    private Long tokenExpiryMinutes = 10L;

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

    /**
     * 현재 인증된 사용자의 ID를 반환합니다.
     * 인증되지 않은 경우 null을 반환합니다.
     */
    public static Integer getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            CustomPrincipal principal = (CustomPrincipal) authentication.getPrincipal();
            return principal.getUserId();
        }

        return null;
    }

    @Transactional
    public void registerProcess(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();
        String nickname = registerRequest.getNickname();
        LocalDate birthDate = registerRequest.getBirthDate();
        PROVIDER provider = PROVIDER.LOCAL;
        Boolean isActive = true;
        Boolean marketingAgreement = registerRequest.getMarketingAgreement();

        Boolean isExists = userRepository.existsByEmail(email);

        if(isExists) {
            throw new RuntimeException(String.format("Email already exists: %s", email));
        }

        User user = User.builder()
                .nickname(nickname)
                .password(passwordEncoder.encode(password))
                .email(email)
                .birthDate(birthDate)
                .provider(provider)
                .role(ROLE_USER)
                .isActive(isActive)
                .marketingAgreement(marketingAgreement)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public void socialJoinProcess(OAuth2RegisterRequest oAuth2RegisterRequest, CustomPrincipal customPrincipal) {

        if (!Objects.equals(customPrincipal.getUserEmail(), oAuth2RegisterRequest.getEmail())) {
            throw new RuntimeException(
                    String.format("Social login email mismatch. Principal email: [%s], Request email: [%s]",
                            customPrincipal.getUserEmail(), oAuth2RegisterRequest.getEmail())
            );
        }

        User user = userRepository.findByEmail(oAuth2RegisterRequest.getEmail())
                .orElseThrow(() -> new RuntimeException(String.format("User not found with email: %s", oAuth2RegisterRequest.getEmail())));

        user.updateSocialUserInfo(
                oAuth2RegisterRequest.getNickname(),
                oAuth2RegisterRequest.getBirthDate(),
                ROLE_USER,
                true,
                oAuth2RegisterRequest.getMarketingAgreement()
        );

        userRepository.save(user);
    }

    public EmailFindResponse findEmail(EmailFindRequest emailFindRequest) {
        String nickname = emailFindRequest.getNickname();
        LocalDate birthDate = emailFindRequest.getBirthDate();

        User user = userRepository.findByNicknameAndBirthDate(nickname, birthDate)
                .orElseThrow(() -> new RuntimeException("User not found with provided nickname and birth date"));

        return EmailFindResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    public void sendResetLink(PasswordResetEmailRequest passwordResetEmailRequest) {

        String email = passwordResetEmailRequest.getEmail();

        if (!userRepository.existsByEmail(email)) {
            throw new RuntimeException(String.format("User not found with email: %s", email));
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpiryMinutes);

        PasswordResetToken entity = PasswordResetToken.builder()
                .email(email)
                .token(token)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();

        passwordResetTokenRepository.save(entity);

        String resetLink = "https://yourservice.com/reset-password?token=" + token;
        String subject = "[인간지표] 비밀번호 재설정 안내";
        String content = "비밀번호 재설정 링크: \n" + resetLink + "\n\n유효 시간: 10분";

        // 이메일 발송
        emailService.sendEmail(email, subject, content);
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmRequest passwordResetConfirmRequest) {
        String email = passwordResetConfirmRequest.getEmail();
        String newPassword = passwordResetConfirmRequest.getNewPassword();
        String token = passwordResetConfirmRequest.getToken();

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenAndIsUsedFalseAndExpiresAtAfter(token, LocalDateTime.now())
                .orElseThrow(() -> new RuntimeException("Invalid or expired password reset token"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Password reset token has expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(String.format("User not found with email: %s", email)));

        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 토큰 사용 처리
        resetToken.setIsUsed();
        passwordResetTokenRepository.save(resetToken);
    }

    @Transactional
    public void withdrawUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(String.format("User not found with email: %s", email)));

        user.withdraw();

        userRepository.save(user);
    }
}
