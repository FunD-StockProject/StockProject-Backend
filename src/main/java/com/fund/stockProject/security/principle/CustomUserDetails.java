package com.fund.stockProject.security.principle;

import com.fund.stockProject.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final User user;

    public CustomUserDetails(String email, String password, Collection<? extends GrantedAuthority> authorities, User user) {
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // getUsername()에는 email 반환 (id를 email로 쓸 경우)
    @Override
    public String getUsername() {
        return email;
    }

    // 커스텀 getter
    public String getEmail() {
        return email;
    }

    // 기타 UserDetails 추상 메서드들 기본 값 설정
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
