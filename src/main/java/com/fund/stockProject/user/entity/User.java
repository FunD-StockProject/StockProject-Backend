package com.fund.stockProject.user.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.auth.domain.PROVIDER;
import com.fund.stockProject.auth.domain.ROLE;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends Core {
    // 공통 부분
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
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

    // 유저 관련 설정
    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean isActive = false;

    @Column(nullable = false)
    private Boolean marketingAgreement = false;

    @Builder
    public User(String email, String nickname, LocalDate birthDate, ROLE role, PROVIDER provider, String providerId,
                String profileImageUrl, Boolean isActive, Boolean marketingAgreement) {
        this.email = email;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.profileImageUrl = profileImageUrl;
        this.isActive = isActive;
        this.marketingAgreement = marketingAgreement;
    }

    public void updateSocialUserInfo(PROVIDER provider, String providerId, String socialAccessToken, String socialRefreshToken) {
        this.provider = provider;
        this.providerId = providerId;
        this.socialAccessToken = socialAccessToken;
        this.socialRefreshToken = socialRefreshToken;
    }

    public void updateProfile(String nickname, LocalDate birthDate, Boolean marketingAgreement) {
        if (nickname != null && !nickname.isBlank()) this.nickname = nickname;
        if (birthDate != null) this.birthDate = birthDate;
        if (marketingAgreement != null) this.marketingAgreement = marketingAgreement;
    }

    public void updateProfileImage(String imageUrl) {
        this.profileImageUrl = imageUrl;
    }
}
