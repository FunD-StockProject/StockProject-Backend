package com.fund.stockProject.user.entity;

import com.fund.stockProject.global.entity.Core;
import com.fund.stockProject.user.domain.ROLE;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private String email; // 주 식별자: 모든 사용자는 고유한 이메일을 가집니다.

    private String password; // 일반 로그인 사용자만 가짐 (BCrypt로 암호화)

    private String nickname; // 사용자에게 보여질 닉네임 (OAuth2에서 제공되거나 가입 시 입력)

    private String birthDate; // 생년월일 (선택적, 소셜 가입 시 입력받을 수 있음)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ROLE role; // 사용자 권한 (예: ROLE_USER, ROLE_ADMIN)

    // OAuth2 제공자 정보
    @Column(nullable = false)
    private String provider; // OAuth2 제공자 (예: "google", "kakao", "naver")

    private String providerId; // OAuth2 제공자별 고유 ID (예: Google의 'sub')

    private String socialAccessToken; // 소셜 로그인 시 발급받은 Access Token

    private String socialRefreshToken; // 소셜 로그인 시 발급받은 Refresh Token

    private LocalDateTime accessTokenExpiresAt; // Access Token 만료 시간 (ISO 8601 형식으로 저장)

    public void updateSocialUserInfo(String nickname, String birthDate) {
        // null 체크 등을 통해 유효성 검사 추가 가능
        if (nickname != null && !nickname.trim().isEmpty()) {
            this.nickname = nickname;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }

    public void updateSocialUserInfo(String socialAccessToken, String socialRefreshToken, LocalDateTime accessTokenExpiresAt) {
        // null 체크 등을 통해 유효성 검사 추가 가능
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
