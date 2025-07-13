package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.ROLE;
import com.fund.stockProject.auth.dto.LoginResponse;
import com.fund.stockProject.auth.dto.RefreshTokenRequest;
import com.fund.stockProject.auth.entity.RefreshToken;
import com.fund.stockProject.auth.repository.RefreshTokenRepository;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fund.stockProject.security.util.JwtUtil.*;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository; // RefreshRepository 주입
    private final UserRepository userRepository;

    @Value("${spring.jwt.access-expiration-ms}")
    private Long accessTokenExpirationMs;

    @Value("${spring.jwt.refresh-expiration-ms}")
    private Long refreshTokenExpirationMs;


    @Transactional
    public LoginResponse issueTokensOnLogin(String email, ROLE role, String existingRefreshToken) {
        // access token 생성
        String accessToken = jwtUtil.createJwt(JWT_CATEGORY_ACCESS, email, role, accessTokenExpirationMs);

        // refresh token 존재하면 삭제
        if (existingRefreshToken != null && !existingRefreshToken.isEmpty()) {
            refreshTokenRepository.deleteByRefreshToken(existingRefreshToken);
        }

        // refresh token 생성
        String newRefreshToken = jwtUtil.createJwt(JWT_CATEGORY_REFRESH, email, role, refreshTokenExpirationMs);

        // refresh token을 DB에 저장
        RefreshToken refreshToken = new RefreshToken(email, newRefreshToken, System.currentTimeMillis() + refreshTokenExpirationMs);
        refreshTokenRepository.save(refreshToken);

        String nickname = getNickname(email); // 사용자 닉네임 조회

        return new LoginResponse(email, nickname, accessToken, newRefreshToken);
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
    @Transactional
    public LoginResponse reissueTokens(RefreshTokenRequest request) { // String 토큰을 직접 받음
        String oldRefreshToken = request.getRefreshToken();

        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new JwtException("Refresh token is required for reissue");
        }

        try {
            // 1. 토큰 카테고리 확인
            String category = jwtUtil.getCategory(oldRefreshToken);
            if (!JWT_CATEGORY_REFRESH.equals(category)) {
                throw new JwtException("Invalid token type. Required: refresh");
            }

            // 2. DB에서 해당 refresh token 존재 여부 확인 (토큰 탈취 대비)
            // 이 과정에서 만료된 토큰이면 어차피 아래로 못 내려옵니다.
            RefreshToken storedToken = refreshTokenRepository.findByRefreshToken(oldRefreshToken)
                    .orElseThrow(() -> new JwtException("Refresh token not found in database"));

            // 3. 토큰 정보 추출 (DB 조회 후 수행하여 DB 부하 감소)
            String email = jwtUtil.getEmail(oldRefreshToken);
            // TODO: getRole 메서드를 수정하여 ROLE enum을 반환하도록 변경하는 것이 좋습니다.
            ROLE role = ROLE.valueOf(jwtUtil.getRole(oldRefreshToken)); // getRole은 ROLE enum을 반환하도록 수정하는 것이 좋음

            // 4. 새 토큰 발급
            // 여기서는 기존 RefreshToken을 DB에서 삭제하고 새로 발급하는 'Refresh Token Rotation' 전략을 사용
            refreshTokenRepository.delete(storedToken);

            String newAccessToken = jwtUtil.createJwt(JWT_CATEGORY_ACCESS, email, role, accessTokenExpirationMs);
            String newRefreshToken = jwtUtil.createJwt(JWT_CATEGORY_REFRESH, email, role, refreshTokenExpirationMs);

            // 5. 새로 발급한 Refresh Token을 DB에 저장
            RefreshToken refreshToken = RefreshToken.builder()
                    .email(email)
                    .refreshToken(newRefreshToken)
                    .expiration(System.currentTimeMillis() + refreshTokenExpirationMs)
                    .build();

            refreshTokenRepository.save(refreshToken);
            String nickname = getNickname(email); // 사용자 닉네임 조회

            // 6. 새 토큰들을 DTO에 담아 반환
            return new LoginResponse(email, nickname, newAccessToken, newRefreshToken);

        } catch (ExpiredJwtException e) {
            // 만료된 경우, DB에 토큰이 남아있다면 삭제해주는 것이 보안상 좋습니다.
            refreshTokenRepository.findByRefreshToken(oldRefreshToken)
                    .ifPresent(refreshTokenRepository::delete);
            throw new JwtException("Refresh token has expired. Please log in again.");
        } catch (JwtException e) {
            // 그 외 JWT 관련 예외 (서명 오류, 형식 오류 등)
            throw new JwtException("Invalid refresh token: " + e.getMessage());
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        // 1. Refresh Token 유효성 검증 (null 또는 비어 있는지)
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token is required for logout.");
        }

        // 2. Refresh Token 유효성 검증 (JWT 파싱)
        // 만료된 토큰으로도 로그아웃은 할 수 있어야 하므로, 만료 예외는 무시하고 진행합니다.
        // 하지만 서명 위조 등 근본적인 문제는 잡아야 합니다.
        try {
            String category = jwtUtil.getCategory(refreshToken);
            if (!JWT_CATEGORY_REFRESH.equals(category)) {
                throw new JwtException("Invalid token. Not a refresh token.");
            }
        } catch (JwtException e) {
            // 서명 오류, 형식 오류 등 진짜 문제 발생 시
            // 이미 탈취되어 변조된 토큰일 수 있으므로 로그만 남기고, 클라이언트에게는 성공처럼 응답할 수도 있습니다.
            // 여기서는 일단 예외를 던져서 컨트롤러에서 처리하도록 합니다.
            throw new JwtException("Invalid refresh token: " + e.getMessage());
        }

        // 3. DB에서 Refresh Token 삭제
        // deleteBy... 메소드는 대상이 없어도 오류를 발생시키지 않으므로, find.. 없이 바로 사용 가능
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
    }

    private String getNickname(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException("User not found with email: " + email))
                .getNickname(); // 사용자 닉네임 조회
    }

}