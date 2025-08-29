package com.fund.stockProject.security.filter;

import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fund.stockProject.security.util.JwtUtil.JWT_CATEGORY_ACCESS;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    // JWT 검증을 건너뛰어야 하는 공개/콜백 엔드포인트들
    private static final List<String> WHITELIST = List.of(
            "/login/oauth2/**",
            "/oauth2/**"
    );

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String p : WHITELIST) {
            if (PATH_MATCHER.match(p, uri)) {
                return true; // 화이트리스트 경로는 필터 스킵
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 이미 인증된 경우는 그대로 다음 필터로 진행
            var currentAuth = SecurityContextHolder.getContext().getAuthentication();
            if (currentAuth != null && currentAuth.isAuthenticated() && !(currentAuth instanceof AnonymousAuthenticationToken)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰 추출
            String accessToken = resolveToken(request);

            // 토큰이 있으면 검증 및 컨텍스트 설정
            if (accessToken != null) {
                processJwtAuthentication(accessToken);
            }

            // 체인은 정확히 한 번만 호출
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // 예외 상황에서만 컨텍스트 정리 후 스프링에 위임
            log.error("JWT 인증 처리 중 오류", e);
            SecurityContextHolder.clearContext();
            throw e;
        }
        // finally 블록 없음: getAsyncContext() 접근 금지 + 중복 체인 호출 방지
    }

    /**
     * JWT 토큰을 처리하고 인증 정보를 설정합니다.
     */
    private void processJwtAuthentication(String accessToken) {
        try {
            if (!JWT_CATEGORY_ACCESS.equals(jwtUtil.getCategory(accessToken))) {
                log.warn("Authorization 헤더에 Access Token이 아닌 토큰이 전달되었습니다. category={}", jwtUtil.getCategory(accessToken));
                return;
            }

            String email = jwtUtil.getEmail(accessToken);
            String role = jwtUtil.getRole(accessToken);

            // ROLE_ 접두어 부여
            List<SimpleGrantedAuthority> authorities = Stream.of(role.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());

            User user = findUserByEmailOptimized(email);
            if (user == null) {
                log.warn("사용자를 찾을 수 없습니다. email={}", email);
                return;
            }

            CustomUserDetails principal = new CustomUserDetails(email, "", authorities, user);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 예기치 못한 오류", e);
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * HttpServletRequest에서 토큰을 추출:
     * 1) Authorization: Bearer <token>
     * 2) query param: token (예: SSE 등)
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }

    /**
     * 이메일로 사용자 조회 (안전하게 실패 허용)
     */
    private User findUserByEmailOptimized(String email) {
        try {
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            log.error("사용자 조회 오류 email={}", email, e);
            return null;
        }
    }
}
