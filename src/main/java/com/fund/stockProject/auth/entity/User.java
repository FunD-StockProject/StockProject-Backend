package com.fund.stockProject.auth.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.domain.ROLE;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends Core {
    // 공통 부분
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private String nickname;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ROLE role;

    // OAuth2 제공자 정보
    @Column(nullable = false)
    private PROVIDER provider;

    private String providerId;

    private String socialAccessToken;

    private String socialRefreshToken;

    private LocalDateTime accessTokenExpiresAt;

//    @Builder
//    public User(String email, String password, String nickname, LocalDate birthDate, ROLE role, PROVIDER provider) {
//        this.email = email;
//        this.password = password;
//        this.nickname = nickname;
//        this.birthDate = birthDate;
//        this.role = role;
//        this.provider = provider;
//    }

    @Builder
    public User(String email, String password, String nickname, LocalDate birthDate, ROLE role, PROVIDER provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
    }

    public void updateSocialUserInfo(String nickname, LocalDate birthDate, ROLE role) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            this.nickname = nickname;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (role != null) {
            this.role = role;
        }
    }

    public void updateSocialUserInfo(String socialAccessToken, String socialRefreshToken, LocalDateTime accessTokenExpiresAt) {
        if (socialAccessToken != null && !socialAccessToken.trim().isEmpty()) {
            this.socialAccessToken = socialAccessToken;
        }
        if (socialRefreshToken != null && !socialRefreshToken.trim().isEmpty()) {
            this.socialRefreshToken = socialRefreshToken;
        }
        if (accessTokenExpiresAt != null) {
            this.accessTokenExpiresAt = accessTokenExpiresAt;
        }
    }
}
