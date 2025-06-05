package com.fund.stockProject.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.repository.RefreshRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.fund.stockProject.security.util.JwtUtil.*;

@Component
@RequiredArgsConstructor
public class CustomLogoutFilter extends OncePerRequestFilter { // ⭐ 상속 변경

    private final JwtUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String requestMethod = request.getMethod();

        // 1. 요청 URI와 메서드 확인: /auth/logout (POST) 요청만 필터 처리
        if (!requestURI.equals("/auth/logout") || !requestMethod.equals("POST")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Refresh Token 추출 (쿠키에서)
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            refreshToken = Arrays.stream(cookies)
                    .filter(cookie -> cookie.getName().equals(REFRESH_TOKEN_COOKIE_NAME)) // "refresh" 대신 상수 사용
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 3. Refresh Token 존재 여부 확인
        if (refreshToken == null) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST.value(), "Refresh token not found");
            return;
        }

        // 4. Refresh Token 유효성 검증
        try {
            // JWTUtil의 getClaims() 호출 시 만료 여부 및 기타 유효성 검사 수행
            String category = jwtUtil.getCategory(refreshToken); // 내부적으로 getClaims() 호출

            // 토큰 카테고리 확인
            if (!category.equals(JWT_CATEGORY_REFRESH)) { // "refresh" 대신 상수 사용
                sendErrorResponse(response, HttpStatus.BAD_REQUEST.value(), "Invalid refresh token category");
                return;
            }
        } catch (ExpiredJwtException e) {
            // 리프레시 토큰이 만료된 경우에도, 해당 토큰으로 로그인 상태를 유지할 수 없으므로 로그아웃 처리
            // 클라이언트에게는 성공을 알리고, DB에서는 삭제 시도
            logger.warn("Expired refresh token received during logout: " + e.getMessage());
            // 하지만 DB 삭제는 시도하고, 쿠키도 삭제해야 함. 응답은 200 OK로 해도 무방.
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            // 유효하지 않은 Refresh Token
            logger.error("Invalid refresh token received during logout: " + e.getMessage());
            sendErrorResponse(response, HttpStatus.BAD_REQUEST.value(), "Invalid refresh token");
            return;
        } catch (Exception e) {
            // 그 외 예상치 못한 예외
            logger.error("An unexpected error occurred during logout: " + e.getMessage(), e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "Logout failed due to an internal error");
            return;
        }

        // 5. Refresh Token DB 삭제 (무효화)
        // (선택 사항) 만료된 토큰이 DB에 남아있을 수 있으므로 deleteByRefresh는 성공 여부와 상관없이 호출
        refreshRepository.deleteByRefreshToken(refreshToken);

        // 6. 쿠키 삭제
        // 액세스 토큰은 보통 헤더에 저장되므로, 쿠키에서 'access' 삭제 로직은 불필요.
        // 클라이언트에서 직접 삭제하도록 유도해야 함.
        CookieUtil.deleteCookie(request, response, REFRESH_TOKEN_COOKIE_NAME); // 리프레시 토큰 쿠키 삭제
        CookieUtil.deleteCookie(request, response, ACCESS_TOKEN_COOKIE_NAME); // 액세스 토큰 쿠키 삭제
        CookieUtil.deleteCookie(request, response, CSRF_TOKEN_COOKIE_NAME); // XSRF-TOKEN 쿠키 삭제 (필요한 경우)
        // XSRF-TOKEN은 CSRF 공격 방어를 위해 사용되는 경우인데,
        // JWT + HttpOnly Refresh Token 조합에서는 XSRF-TOKEN이 일반적으로 필요 없습니다.
        // 만약 사용한다면 Spring Security의 CSRF 설정에 따라 처리.
        // deleteCookie(response, "XSRF-TOKEN"); // 필요하다면 유지

        // 7. 로그아웃 성공 응답
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Logout successful");
        objectMapper.writeValue(response.getWriter(), responseBody);

        filterChain.doFilter(request, response);
    }

    // ⭐ sendErrorResponse 헬퍼 메서드를 JWTFilter에서 가져와 재사용
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
