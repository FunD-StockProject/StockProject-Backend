package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.ROLE;
import com.fund.stockProject.auth.dto.LoginResponse;
import com.fund.stockProject.auth.dto.RefreshTokenRequest;
import com.fund.stockProject.auth.entity.RefreshToken;
import com.fund.stockProject.auth.repository.RefreshTokenRepository;
import com.fund.stockProject.notification.service.DeviceTokenService;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fund.stockProject.security.util.JwtUtil.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository; // RefreshRepository 주입
    private final UserRepository userRepository;
    private final DeviceTokenService deviceTokenService;

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

        UserProfile userProfile = getUserProfile(email); // 사용자 닉네임/프로필 이미지 조회

        return new LoginResponse("SUCCESS", email, userProfile.nickname(), userProfile.profileImageUrl(), accessToken, newRefreshToken);
    }

    /**
     * 리프레시 토큰을 사용하여 새로운 액세스 토큰과 새로운 리프레시 토큰을 발급합니다.
     * 이 과정에서 사용된 기존 리프레시 토큰은 DB에서 삭제됩니다 (Refresh Token Rotation).
     *
     * @param request 리프레시 토큰이 담긴 요청 DTO
     * @return 새로 발급된 액세스/리프레시 토큰 정보를 담은 LoginResponse
     * @throws RuntimeException 토큰 검증 실패 시 (만료, 유효하지 않음 등)
     */
    @Transactional
    public LoginResponse reissueTokens(RefreshTokenRequest request) { // String 토큰을 직접 받음
        String oldRefreshToken = request.getRefreshToken();

        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new JwtException("Refresh token is required for reissue");
        }

        try {
            // 1. DB에서 해당 refresh token 존재 여부 확인 (토큰 탈취 대비)
            // DB 조회를 먼저 하여 유효한 토큰인지 확인 (만료된 토큰도 DB에 남아있을 수 있으므로)
            RefreshToken storedToken = refreshTokenRepository.findByRefreshToken(oldRefreshToken)
                    .orElseThrow(() -> new JwtException("Refresh token not found in database"));

            // 2. 토큰 카테고리 확인 (DB 조회 후 수행)
            String category = jwtUtil.getCategory(oldRefreshToken);
            if (!JWT_CATEGORY_REFRESH.equals(category)) {
                throw new JwtException("Invalid token type. Required: refresh");
            }

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
            UserProfile userProfile = getUserProfile(email); // 사용자 닉네임/프로필 이미지 조회

            // 6. 새 토큰들을 DTO에 담아 반환
            return new LoginResponse("SUCCESS", email, userProfile.nickname(), userProfile.profileImageUrl(), newAccessToken, newRefreshToken);

        } catch (ExpiredJwtException e) {
            // 만료된 경우, DB에 토큰이 남아있다면 삭제해주는 것이 보안상 좋습니다.
            // ExpiredJwtException은 JwtException의 하위 클래스이므로 먼저 처리해야 합니다.
            try {
                refreshTokenRepository.findByRefreshToken(oldRefreshToken)
                        .ifPresent(refreshTokenRepository::delete);
            } catch (Exception deleteException) {
                log.warn("Failed to delete expired refresh token from database", deleteException);
            }
            throw new JwtException("Refresh token has expired. Please log in again.");
        } catch (JwtException e) {
            // 그 외 JWT 관련 예외 (서명 오류, 형식 오류 등)
            // ExpiredJwtException이 아닌 다른 JwtException인 경우
            throw new JwtException("Invalid refresh token: " + e.getMessage());
        } catch (Exception e) {
            // 예상치 못한 예외 (예: IllegalArgumentException from ROLE.valueOf)
            log.error("Unexpected error during token reissue", e);
            throw new JwtException("Invalid refresh token: " + e.getMessage());
        }
    }

    @Transactional
    public void logout(Integer userId, String refreshToken, String deviceToken) {
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

        // 4. 디바이스 토큰 비활성화
        // 멀티 디바이스 환경 보호:
        // deviceToken이 전달된 경우에만 해당 토큰을 비활성화
        Integer resolvedUserId = resolveUserId(userId, refreshToken);
        if (resolvedUserId != null) {
            deviceTokenService.unregisterOnLogout(resolvedUserId, deviceToken);
        } else {
            log.warn("Skip device token cleanup during logout: cannot resolve user. hasAuthPrincipal={}", userId != null);
        }
    }

    private Integer resolveUserId(Integer authenticatedUserId, String refreshToken) {
        if (authenticatedUserId != null) {
            return authenticatedUserId;
        }

        try {
            String email = jwtUtil.getEmail(refreshToken);
            return userRepository.findByEmail(email).map(User::getId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to resolve userId from refresh token during logout: {}", e.getMessage());
            return null;
        }
    }

    private UserProfile getUserProfile(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException("User not found with email: " + email));
        return new UserProfile(user.getNickname(), user.getProfileImageUrl());
    }

    private record UserProfile(String nickname, String profileImageUrl) {}

}
