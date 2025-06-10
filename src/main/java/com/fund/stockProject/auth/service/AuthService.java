package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.OAuth2RegisterRequest;
import com.fund.stockProject.auth.dto.RegisterRequest;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomPrincipal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

import static com.fund.stockProject.auth.domain.ROLE.ROLE_USER;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
}
