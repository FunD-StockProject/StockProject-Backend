package com.fund.stockProject.security.util;

import com.fund.stockProject.auth.domain.ROLE;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    // SecretKey는 immutable하므로 thread-safe함
    private final SecretKey secretKey;

    public static final String JWT_CATEGORY_ACCESS = "access";
    public static final String JWT_CATEGORY_REFRESH = "refresh";

    public JwtUtil(@Value("${spring.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT 유틸리티가 초기화되었습니다.");
    }

    /**
     * 토큰 파싱 및 클레임 반환 메서드 (예외 처리는 호출하는 쪽에서)
     * JWT 파서는 thread-safe하므로 동시 호출이 가능합니다.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 토큰에서 카테고리를 추출합니다.
     * @param token JWT 토큰
     * @return 토큰 카테고리
     */
    public String getCategory(String token) {
        return getClaims(token).get("category", String.class);
    }

    /**
     * 토큰에서 이메일을 추출합니다.
     * @param token JWT 토큰
     * @return 사용자 이메일
     */
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * 토큰에서 역할을 추출합니다.
     * @param token JWT 토큰
     * @return 사용자 역할
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * JWT 토큰을 생성합니다.
     * 이 메서드는 thread-safe합니다.
     * @param category 토큰 카테고리
     * @param email 사용자 이메일
     * @param role 사용자 역할
     * @param expiredMs 만료 시간 (밀리초)
     * @return 생성된 JWT 토큰
     */
    public String createJwt(String category, String email, ROLE role, Long expiredMs) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusMillis(expiredMs);

        return Jwts.builder()
                .claim("category", category)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }
}
