package com.fund.stockProject.security.principle;

import com.fund.stockProject.auth.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomPrincipal implements UserDetails, OAuth2User {

    private User user; // 실제 User 엔티티
    private Map<String, Object> attributes; // OAuth2User 속성 (OAuth2 로그인 시에만 값 가짐)
    private String socialAccessToken; // OAuth2 로그인 시 사용되는 액세스 토큰 (선택적)
    private Boolean isNewUser = false; // 새 사용자 여부 (선택적, 필요시 사용)
    // 일반 로그인 사용자를 위한 생성자
    public CustomPrincipal(User user) {
        this.user = user;
    }

    // OAuth2 로그인 사용자를 위한 생성자
    public CustomPrincipal(User user, Map<String, Object> attributes, String socialAccessToken, Boolean isNewUser) {
        this.user = user;
        this.attributes = attributes;
        this.socialAccessToken = socialAccessToken; // OAuth2 로그인 시 사용되는 액세스 토큰
        this.isNewUser = isNewUser; // 새 사용자 여부
    }

    public CustomPrincipal(User user, Boolean isNewUser) {
        this.user = user;
        this.isNewUser = isNewUser; // 새 사용자 여부
    }


    // --- UserDetails 인터페이스 구현 (Spring Security 내부 사용) ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // 일반 로그인 사용자의 비밀번호 반환
    }

    @Override
    public String getUsername() {
        // ✅ UserDetails의 'username'을 이메일로 매핑하여 Spring Security 내부에서 사용
        return user.getEmail();
    }

    // 계정 상태 관련 메서드 (true로 반환하여 기본적으로 활성화 상태)
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    // --- OAuth2User 인터페이스 구현 (Spring Security 내부 사용) ---
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        // ✅ OAuth2User의 'name'을 애플리케이션의 주 식별자인 이메일로 통일
        return user.getEmail();
    }

    // --- ✨ 애플리케이션 로직에서 사용할 공통 Getter (핵심) ✨ ---
    public String getUserEmail() {
        // ✨ 개발자는 이메일을 가져올 때 항상 이 메서드를 사용하도록 권장합니다.
        //    getUsername(), getName()과의 혼란을 방지하고 일관성을 제공합니다.
        return user.getEmail();
    }

    public String getUserNickname() {
        // 사용자 닉네임을 가져올 때 사용합니다.
        return user.getNickname();
    }
}
