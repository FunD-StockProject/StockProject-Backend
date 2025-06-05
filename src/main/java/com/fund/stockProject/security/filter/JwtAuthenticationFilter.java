package com.fund.stockProject.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.principle.CustomPrincipal;
import com.fund.stockProject.security.util.CookieUtil;
import com.fund.stockProject.security.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

import static com.fund.stockProject.security.util.JwtUtil.*;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // 임시 토큰으로 접근 가능한 경로들
    private static final List<String> TEMP_TOKEN_ALLOWED_PATHS = Arrays.asList(
            "/auth/oauth2/",
            "/oauth2/authorization",       // OAuth2 인가 기본 경로 (이하 모든 경로 허용)
            "/login/oauth2"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = null;
        String tempToken = null;

        // 1. 요청에서 토큰 쿠키들 추출
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    accessToken = cookie.getValue();
                } else if (TEMP_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    tempToken = cookie.getValue();
                }
            }
        }

        // 2. 토큰 우선순위: Access Token > Temp Token
        String tokenToProcess = null;
        boolean isTempToken = false;

        if (accessToken != null) {
            tokenToProcess = accessToken;
            isTempToken = false;
        } else if (tempToken != null) {
            tokenToProcess = tempToken;
            isTempToken = true;
        }

        // 토큰이 아예 없는 경우: 인증 컨텍스트를 비우고 다음 필터로 진행
        if (tokenToProcess == null) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 토큰 유효성 검증 및 처리
        try {
            // JWT 토큰 유효성 검증 및 클레임 추출
            String category = jwtUtil.getCategory(tokenToProcess);
            String email = jwtUtil.getEmail(tokenToProcess);
            String role = jwtUtil.getRole(tokenToProcess);

            if (isTempToken) {
                // 임시 토큰 처리
                handleTempToken(request, response, filterChain, category, email, role);
            } else {
                // 액세스 토큰 처리
                handleAccessToken(request, response, filterChain, category, email, role);
            }

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            if (isTempToken) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Temporary token expired");
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Access token expired");
            }

        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;

        } catch (UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;

        } catch (Exception e) {
            logger.error("An unexpected error occurred during JWT authentication", e);
            SecurityContextHolder.clearContext();
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error");
            return;
        }
    }

    /**
     * 임시 토큰 처리
     */
    private void handleTempToken(HttpServletRequest request, HttpServletResponse response,
                                 FilterChain filterChain, String category, String email, String role)
            throws IOException, ServletException {

        // 카테고리 검증
        if (!JWT_CATEGORY_TEMP.equals(category)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // 임시 토큰으로 접근 가능한 경로인지 확인
        String requestURI = request.getRequestURI();
        if (!isAllowedPathForTempToken(requestURI)) {
            SecurityContextHolder.clearContext();
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Temporary token can only access registration completion endpoints");
            return;
        }

        // 임시 사용자 인증 객체 생성
        createTempAuthentication(email, role);
        filterChain.doFilter(request, response);
    }

    /**
     * 액세스 토큰 처리 (기존 로직)
     */
    private void handleAccessToken(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain filterChain, String category, String email, String role)
            throws IOException, ServletException {

        // 혹시 있을 임시 토큰 제거
        CookieUtil.deleteCookie(request, response, TEMP_TOKEN_COOKIE_NAME);

        // 토큰 카테고리 검증
        if (!category.equals(JWT_CATEGORY_ACCESS)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // 데이터베이스에서 실제 사용자 정보 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Spring Security 컨텍스트에 인증 정보 설정
        CustomPrincipal customPrincipal = new CustomPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                customPrincipal,
                null,
                customPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * 임시 토큰으로 접근 가능한 경로인지 확인
     */
    private boolean isAllowedPathForTempToken(String requestURI) {
        return TEMP_TOKEN_ALLOWED_PATHS.stream()
                .anyMatch(requestURI::startsWith);
    }

    /**
     * 임시 사용자 인증 객체 생성
     */
    private void createTempAuthentication(String email, String role) {
        // 임시 사용자를 위한 CustomPrincipal 생성 (isNewUser = true)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email)); // 이것 다시 보기

        // TODO: 여기 다시 구현
        CustomPrincipal tempPrincipal = new CustomPrincipal(user, true);

        // 임시 사용자 권한 설정
        Collection<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_TEMP")
        );

        Authentication authToken = new UsernamePasswordAuthenticationToken(
                tempPrincipal, null, authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("status", status);
        errorDetails.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
        response.getWriter().flush();
    }
}
