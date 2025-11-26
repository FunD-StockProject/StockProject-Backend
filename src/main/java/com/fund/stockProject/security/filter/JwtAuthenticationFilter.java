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
import org.springframework.security.core.Authentication;
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

        String requestUri = request.getRequestURI();
        log.info("JWT 필터 실행 - URI: {}, Method: {}", requestUri, request.getMethod());
        
        String accessToken = resolveToken(request);
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

        if (accessToken != null) {
            log.info("Access token 발견 - URI: {}, token length: {}", requestUri, accessToken.length());
            try {
                if (!isReusable(currentAuth, accessToken)) {
                    log.info("새로운 인증 처리 시작 - URI: {}", requestUri);
                    processJwtAuthentication(accessToken, requestUri); // JWT 파싱/검증
                } else {
                    log.info("재사용 가능한 인증 정보 사용 - URI: {}", requestUri);
                }
            } catch (ExpiredJwtException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
                log.warn("JWT 검증 실패 - URI: {}, Error: {}", requestUri, e.getMessage());
                SecurityContextHolder.clearContext(); // 토큰 자체 문제일 때만 초기화
            }
        } else {
            log.warn("Access token이 없습니다 - URI: {}, Authorization header: {}", 
                    requestUri, request.getHeader("Authorization"));
        }

        // 인증 후 SecurityContext 상태 확인
        Authentication authAfter = SecurityContextHolder.getContext().getAuthentication();
        if (authAfter != null && authAfter.isAuthenticated()) {
            log.info("인증 완료 - URI: {}, authenticated: true", requestUri);
        } else {
            log.warn("인증 실패 또는 미인증 상태 - URI: {}, auth: {}", requestUri, authAfter);
        }

        // 비즈니스 예외는 여기서 잡지 않고 그대로 상위로 전달 (double dispatch 방지)
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // async 재디스패치에도 필터 실행하여 SecurityContext 재구성
        return false;
    }

    /**
     * JWT 토큰을 처리하고 인증 정보를 설정합니다.
     */
    private void processJwtAuthentication(String accessToken, String requestUri) {
        try {
            String category = jwtUtil.getCategory(accessToken);
            if (!JWT_CATEGORY_ACCESS.equals(category)) {
                log.warn("Authorization 헤더에 Access Token이 아닌 토큰이 전달되었습니다. URI: {}, category={}", requestUri, category);
                SecurityContextHolder.clearContext();
                return;
            }

            String email = jwtUtil.getEmail(accessToken);
            String role = jwtUtil.getRole(accessToken);

            // ROLE_ 접두어 중복 방지 및 중복 권한 제거
            List<SimpleGrantedAuthority> authorities = Stream.of(role.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            User user = findUserByEmailOptimized(email);
            if (user == null) {
                log.warn("사용자를 찾을 수 없습니다. URI: {}, email={}", requestUri, email);
                SecurityContextHolder.clearContext();
                return;
            }

            CustomUserDetails principal = new CustomUserDetails(email, "", authorities, user);

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT 인증 성공 - URI: {}, email={}", requestUri, email);

        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.warn("유효하지 않은 JWT 토큰 - URI: {}, Error: {}", requestUri, e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰 - URI: {}, Error: {}", requestUri, e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 예기치 못한 오류 - URI: {}", requestUri, e);
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

    private boolean isReusable(Authentication auth, String token) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return false;
        if (!(auth.getPrincipal() instanceof CustomUserDetails principal)) return false;
        try {
            if (!JWT_CATEGORY_ACCESS.equals(jwtUtil.getCategory(token))) return false;
            String emailFromToken = jwtUtil.getEmail(token);
            return principal.getEmail().equals(emailFromToken);
        } catch (Exception e) {
            return false;
        }
    }
}
