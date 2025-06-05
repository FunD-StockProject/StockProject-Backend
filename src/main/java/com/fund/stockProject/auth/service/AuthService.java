package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.dto.OAuth2RegisterRequest;
import com.fund.stockProject.auth.dto.RegisterRequest;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomPrincipal;
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

    public void registerProcess(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail();
        String password = registerRequest.getPassword();
        String nickname = registerRequest.getNickname();
        LocalDate birthDate = registerRequest.getBirthDate();
        PROVIDER provider = PROVIDER.LOCAL;

        Boolean isExists = userRepository.existsByEmail(email);

        if(isExists) {
            // 이미 존재하는 닉네임인 경우 예외 처리 또는 다른 로직을 추가할 수 있습니다.
            throw new RuntimeException("이미 존재하는 이메일입니다: " + email);
        }

        User user = User.builder()
                .nickname(nickname)
                .password(passwordEncoder.encode(password))
                .email(email)
                .birthDate(birthDate)
                .provider(provider)
                .role(ROLE_USER)
                .build();

        userRepository.save(user);
    }

    public void socialJoinProcess(OAuth2RegisterRequest oAuth2RegisterRequest, CustomPrincipal customPrincipal) {

        if (!Objects.equals(customPrincipal.getUserEmail(), oAuth2RegisterRequest.getEmail())) {
            throw new RuntimeException("소셜 로그인 이메일과 클라이언트가 입력한 이메일이 일치하지 않습니다.");
        }

        User user = userRepository.findByEmail(oAuth2RegisterRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다. 이메일: " + oAuth2RegisterRequest.getEmail()));

        user.updateSocialUserInfo(
                oAuth2RegisterRequest.getNickname(),
                oAuth2RegisterRequest.getBirthDate(),
                ROLE_USER
        );

        userRepository.save(user);
    }
}
