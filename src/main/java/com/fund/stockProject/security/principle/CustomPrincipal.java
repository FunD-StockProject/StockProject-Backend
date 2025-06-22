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
    public String getUsername() { // 일반적으로 사용 X
        return user.getEmail(); // 사용자 이메일을 반환
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
    public String getName() { // 일반적으로 사용 X
        return user.getEmail(); // 사용자 이메일을 반환
    }

    public Integer getUserId() {
        return user.getId();
    }

    public String getUserEmail() {
        return user.getEmail();
    }

    public String getUserNickname() {
        return user.getNickname();
    }
}
