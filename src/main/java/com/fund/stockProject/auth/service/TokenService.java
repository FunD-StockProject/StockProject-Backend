package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.entity.RefreshToken;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.RefreshRepository;
import com.fund.stockProject.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static com.fund.stockProject.security.util.JwtUtil.*;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final RefreshRepository refreshRepository; // RefreshRepository 주입

    @Value("${spring.jwt.access-expiration-ms}")
    private Long accessTokenExpirationMs;

    @Value("${spring.jwt.refresh-expiration-ms}")
    private Long refreshTokenExpirationMs;

    // 임시 토큰 만료 시간 (10분)
    @Value("${spring.jwt.temp-expiration-ms}")
    private Long TEMP_TOKEN_EXPIRATION_MS;

    /**
     * 새로운 사용자를 위한 임시 토큰 발급
     * 회원가입 완료 시까지만 유효한 임시 토큰을 발급합니다.
     *
     * @param response HttpServletResponse 객체 (쿠키 설정을 위함)
     * @param email 사용자 이메일
     * @param role 사용자 역할
     */
    public void issueTempTokenForNewUser(HttpServletResponse response, String email, String role) {
        // 임시 토큰 생성 (짧은 만료시간, TEMP 카테고리)
        String tempToken = jwtUtil.createJwt(JWT_CATEGORY_TEMP, email, role, TEMP_TOKEN_EXPIRATION_MS);

        // 임시 토큰을 HttpOnly, Secure, SameSite 쿠키에 설정
        ResponseCookie tempTokenCookie = ResponseCookie.from(TEMP_TOKEN_COOKIE_NAME, tempToken)
                .httpOnly(true) // JavaScript에서 접근 불가 (XSS 방지)
                .secure(true)   // HTTPS에서만 전송 (운영 환경 필수)
                .path("/")      // 모든 경로에서 접근 가능
                .maxAge(Duration.ofMillis(TEMP_TOKEN_EXPIRATION_MS)) // 10분
                .sameSite("None") // CSRF 방지
                .build();
        response.addHeader("Set-Cookie", tempTokenCookie.toString());
    }

    /**
     * 임시 토큰을 정식 토큰으로 교환 (회원가입 완료 시 사용)
     *
     * @param request HttpServletRequest 객체 (임시 토큰 쿠키 읽기)
     * @param response HttpServletResponse 객체 (정식 토큰 쿠키 설정)
     * @return 교환 성공 여부
     */
    @Transactional
    public boolean exchangeTempTokenToFullToken(HttpServletRequest request, HttpServletResponse response) {
        // 임시 토큰 추출
        String tempToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (TEMP_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    tempToken = cookie.getValue();
                    break;
                }
            }
        }

        if (tempToken == null) {
            return false;
        }

        try {
            // 임시 토큰 검증
            String email = jwtUtil.getEmail(tempToken);
            String role = jwtUtil.getRole(tempToken);
            String category = jwtUtil.getCategory(tempToken);

            // 카테고리가 TEMP인지 확인
            if (!"TEMP".equals(category)) {
                return false;
            }

            // 임시 토큰 쿠키 삭제
            ResponseCookie deleteTempCookie = ResponseCookie.from(TEMP_TOKEN_COOKIE_NAME, "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(0) // 즉시 만료
                    .sameSite("None")
                    .build();
            response.addHeader("Set-Cookie", deleteTempCookie.toString());

            // 정식 토큰 발급
            issueTokensOnLogin(response, null, email, role);
            return true;

        } catch (Exception e) {
            // 토큰 검증 실패
            return false;
        }
    }

    /**
     * 로그인 성공 시 액세스 토큰과 리프레시 토큰을 발급하고,
     * 리프레시 토큰은 DB에 저장 및 HttpOnly 쿠키로 설정합니다.
     *
     * @param response HttpServletResponse 객체 (쿠키 설정을 위함)
     * @param email 사용자 이메일 (JWT 클레임)
     * @param role 사용자 역할 (JWT 클레임)
     * @return 발급된 액세스 토큰 문자열
     */
    @Transactional
    public void issueTokensOnLogin(HttpServletResponse response, String existingRefreshToken, String email, String role) {
        // 1. Access Token 생성
        String accessToken = jwtUtil.createJwt(JWT_CATEGORY_ACCESS, email, role, accessTokenExpirationMs);

        // 2. Refresh Token 생성
        String newRefreshToken = jwtUtil.createJwt(JWT_CATEGORY_REFRESH, email, role, refreshTokenExpirationMs);

        // 3. (선택적) 기존 Refresh Token이 DB에 있다면 삭제 (로그인 시 보통 새로운 세션으로 간주)
        // 기존 Refresh Token이 있다면 해당 토큰을 무효화하는 것이 좋습니다.
        if (existingRefreshToken != null && !existingRefreshToken.isEmpty()) {
            refreshRepository.deleteByRefreshToken(existingRefreshToken);
        }

        // 4. 새로운 Refresh Token을 DB에 저장
        // RefreshEntity에 @NoArgsConstructor와 @AllArgsConstructor 또는 빌더 패턴을 적용하는 것이 좋습니다.
        RefreshToken refreshToken = new RefreshToken(email, newRefreshToken, System.currentTimeMillis() + refreshTokenExpirationMs);
        refreshRepository.save(refreshToken);

        // 5. Access Token을 HttpOnly, Secure, SameSite=Lax 쿠키에 설정
        // ResponseCookie를 사용하여 쿠키 설정을 더욱 명확하고 안전하게 관리하는 것을 권장합니다.
        ResponseCookie accessTokenCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken) // "access_token" 쿠키 이름은 상수로 관리
                .httpOnly(true) // JavaScript에서 접근 불가 (XSS 방지)
                .secure(true)   // HTTPS에서만 전송 (운영 환경 필수)
                .path("/")      // 모든 경로에서 접근 가능
                .maxAge(Duration.ofMillis(accessTokenExpirationMs)) // Duration 사용
                .sameSite("None") // CSRF 방지: Lax 또는 None (None 선택 시 반드시 Secure=true)
                .build();
        response.addHeader("Set-Cookie", accessTokenCookie.toString());

        // 6. Refresh Token을 HttpOnly, Secure, SameSite=Lax 쿠키에 설정
        ResponseCookie refreshTokenCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, newRefreshToken) // "refresh_token" 쿠키 이름은 상수로 관리
                .httpOnly(true) // JavaScript에서 접근 불가 (XSS 방지)
                .secure(true)   // HTTPS에서만 전송 (운영 환경 필수)
                .path("/")      // 모든 경로에서 접근 가능
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs)) // Duration 사용
                .sameSite("None") // CSRF 방지: Lax 또는 None (None 선택 시 반드시 Secure=true)
                .build();
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());

        // Access Token을 반환하지 않으므로 메서드 시그니처를 void로 변경
    }

    /**
     * 리프레시 토큰을 사용하여 새로운 액세스 토큰과 새로운 리프레시 토큰을 발급합니다.
     * 이 과정에서 사용된 기존 리프레시 토큰은 DB에서 삭제됩니다 (Refresh Token Rotation).
     *
     * @param oldRefreshToken 클라이언트로부터 받은 기존 리프레시 토큰 문자열
     * @param response HttpServletResponse 객체 (새로운 리프레시 토큰 쿠키 설정을 위함)
     * @return 새로 발급된 액세스 토큰 문자열
     * @throws RuntimeException 토큰 검증 실패 시 (만료, 유효하지 않음 등)
     */
    @Transactional // DB 변경 (기존 토큰 삭제, 새 토큰 저장)이 발생하므로 트랜잭션 필요
    public void reissueTokens(HttpServletRequest request, HttpServletResponse response) {

        String oldRefreshToken = null;

        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                oldRefreshToken = cookie.getValue();
                break; // 쿠키를 찾으면 반복 종료
            }
        }

        if (oldRefreshToken == null) {
            throw new RuntimeException("Refresh token not found");
        }

        try {
            // 2. 토큰 정보 추출
            String email = jwtUtil.getEmail(oldRefreshToken);
            String role = jwtUtil.getRole(oldRefreshToken);
            String category = jwtUtil.getCategory(oldRefreshToken);

            // 3. 카테고리 확인
            if (!JWT_CATEGORY_REFRESH.equals(category)) {
                throw new RuntimeException("Invalid refresh token category");
            }

            // 4. DB에서 해당 refresh token 존재 여부 확인
            refreshRepository.findByRefreshToken(oldRefreshToken)
                    .orElseThrow(() -> new RuntimeException("Refresh token not found in database"));

            // 5. 새 토큰 발급
            issueTokensOnLogin(response, oldRefreshToken, email, role);

        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Refresh token expired");
        } catch (JwtException e) {
            throw new RuntimeException("Invalid refresh token: " + e.getMessage());
        }
    }
}