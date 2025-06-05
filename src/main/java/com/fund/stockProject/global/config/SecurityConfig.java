package com.fund.stockProject.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.repository.HttpCookieOAuth2AuthorizationRequestRepository;
import com.fund.stockProject.auth.repository.RefreshRepository;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.security.filter.CustomLogoutFilter;
import com.fund.stockProject.security.filter.JwtAuthenticationFilter;
import com.fund.stockProject.security.filter.JwtLoginFilter;
import com.fund.stockProject.security.handler.JwtLoginSuccessHandler;
import com.fund.stockProject.security.handler.LoginFailureHandler;
import com.fund.stockProject.security.handler.LogoutSuccessHandler;
import com.fund.stockProject.security.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtUtil jwtUtil;
//    private final CustomOAuth2UserService customOAuth2UserService;
//    // 커스텀 핸들러들 직접 등록해줘야 함
//    private final OAuth2LoginSuccessHandler OAuth2LoginSuccessHandler;
    private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RefreshRepository refreshRepository, LogoutSuccessHandler logoutSuccessHandler) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // setCsrfRequestAttributeName(null)은 RequestAttributeHandler가 요청 속성에 CSRF 토큰을 노출하도록 합니다.
        // 이는 CsrfTokenRepository가 쿠키로 토큰을 설정하는 데 도움을 줍니다.
        requestHandler.setCsrfRequestAttributeName(null); // Spring Security 6.0+에서 토큰이 자동으로 쿠키로 전송되게 하는 설정

        http
                .csrf((csrf) -> csrf
                        .ignoringRequestMatchers(
                                request -> "/auth/register".equals(request.getRequestURI()), // 회원가입
                                request -> "/auth/login".equals(request.getRequestURI()),    // 로그인
                                request -> "/auth/social/register".equals(request.getRequestURI()) // 소셜 회원가입
                                // 필요하다면 PathMatcher를 사용하여 더 복잡한 패턴 처리 가능
                                // request -> new AntPathMatcher().match("/auth/**", request.getRequestURI())
                        )
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // ⭐ 이 부분이 중요!
                        .csrfTokenRequestHandler(requestHandler)); // ⭐ 이 부분도 중요!

        http
                .cors((cors) -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOrigins(Collections.singletonList("http://localhost:3000")); // TODO: 실제 도메인으로 변경 필요
                        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                        config.setAllowCredentials(true);
                        config.setAllowedHeaders(Collections.singletonList("*"));
                        config.setMaxAge(3600L);
                        config.setExposedHeaders(Arrays.asList("access", "Set-Cookie"));


                        return config;
                    }
                }));
        http
                .formLogin((auth) -> auth.disable());
        http
                .httpBasic((auth) -> auth.disable());
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/error", "/reissue", "/favicon.ico",
                                "/auth/register","/auth/social/register","/auth/oauth2/naver", "/auth/oauth2/kakao", "/auth/oauth2/google").permitAll()
                        .anyRequest().authenticated());
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none()); // 세션 고정 방지 비활성화, JWT 기반 인증이므로

        http
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userRepository, objectMapper), LogoutFilter.class);
        // 커스텀 필터 등록
        JwtLoginFilter jwtLoginFilter = new JwtLoginFilter(authenticationManager(authenticationConfiguration));
        jwtLoginFilter.setAuthenticationSuccessHandler(jwtLoginSuccessHandler);
        jwtLoginFilter.setAuthenticationFailureHandler(loginFailureHandler);
        jwtLoginFilter.setFilterProcessesUrl("/auth/login");

        http
                .addFilterAt(jwtLoginFilter, UsernamePasswordAuthenticationFilter.class);

        http
                .addFilterAt(new CustomLogoutFilter(jwtUtil, refreshRepository, objectMapper), LogoutFilter.class);

//        http
//                .oauth2Login((oauth2) -> oauth2
//                        .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
//                                .userService(customOAuth2UserService))
//                        .successHandler(OAuth2LoginSuccessHandler)
//                        .failureHandler(loginFailureHandler)
//                        .authorizationEndpoint(authorization -> authorization
//                                .authorizationRequestRepository(cookieAuthorizationRequestRepository())
//                        )
//                );

        http
                .logout((logout) -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll());

        // 이거 다시 보기
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다");
                })
        );

        return http.build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository();
    }
}
