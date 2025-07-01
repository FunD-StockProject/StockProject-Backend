package com.fund.stockProject.security.util;

import com.fund.stockProject.auth.domain.ROLE;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public static final String JWT_CATEGORY_ACCESS = "access";
    public static final String JWT_CATEGORY_REFRESH = "refresh";

    public JwtUtil(@Value("${spring.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 토큰 파싱 및 클레임 반환 메서드 (예외 처리는 호출하는 쪽에서)
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getCategory(String token) {
        return getClaims(token).get("category", String.class);
    }

    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

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
