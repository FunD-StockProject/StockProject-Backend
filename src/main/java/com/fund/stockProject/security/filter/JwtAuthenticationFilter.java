package com.fund.stockProject.security.filter;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.file.attribute.UserPrincipal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fund.stockProject.security.util.JwtUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil; // JwtUtil -> JwtTokenProvider로 이름 변경 가정

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. [수정] Authorization 헤더에서 Bearer 토큰 추출
        String accessToken = resolveToken(request);

        // 2. 토큰이 없는 경우: 다음 필터로 바로 통과 (인증이 필요 없는 API 접근 허용)
        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 토큰 유효성 검증 및 처리
        try {
            if (JWT_CATEGORY_ACCESS.equals(jwtUtil.getCategory(accessToken))) {
                // 토큰이 유효하면, 인증 객체(Authentication)를 가져와서 SecurityContext에 저장
                String email = jwtUtil.getEmail(accessToken);
                String role = jwtUtil.getRole(accessToken);

                List<SimpleGrantedAuthority> authorities = Stream.of(role.split(","))
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                        .collect(Collectors.toList());

                // TODO: 필요에 따라서는 다시 CustomDetails 만들어야 할 듯
                CustomUserDetails userPrincipal = new CustomUserDetails(email, "", authorities);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userPrincipal,
                                null,
                                userPrincipal.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Access Token이 아닌 다른 종류의 토큰(예: Refresh Token)이 헤더에 담겨 온 경우
                log.warn("Authorization 헤더에 Access Token이 아닌 토큰이 전달되었습니다. Category: {}", jwtUtil.getCategory(accessToken));
            }


        } catch (MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            // SecurityContext를 비우는 것이 안전합니다.
            SecurityContextHolder.clearContext();
            // 잘못된 토큰의 경우, 401 Unauthorized 에러를 응답할 수 있습니다.
            // response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Token");
            // 혹은 그냥 통과시켜서 Spring Security의 ExceptionTranslationFilter가 처리하게 둘 수도 있습니다.

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            // 만료된 토큰의 경우, 401 Unauthorized 에러를 응답하여 클라이언트가 토큰을 갱신하도록 유도합니다.
            // TODO: 리턴 값에 따라 reissue
            // response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Expired Token");

        } catch (Exception e) {
            log.error("An unexpected error occurred during JWT authentication", e);
            SecurityContextHolder.clearContext();
            // response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication Error");
        }

        // 4. 다음 필터로 제어 전달
        filterChain.doFilter(request, response);
    }

    /**
     * [새로운 메소드]
     * HttpServletRequest의 Authorization 헤더에서 Bearer 토큰을 추출하는 헬퍼 메소드
     * @param request The request
     * @return 추출된 토큰 문자열, 없거나 형식이 틀리면 null
     */
    private String resolveToken(HttpServletRequest request) {
        // "Authorization" 헤더 값을 가져옵니다.
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 헤더가 존재하고, "Bearer "로 시작하는지 확인합니다.
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer " 접두사를 제거하고 순수한 토큰 값만 반환합니다.
            return bearerToken.substring(7);
        }

        // 그 외의 경우 null을 반환합니다.
        return null;
    }
}