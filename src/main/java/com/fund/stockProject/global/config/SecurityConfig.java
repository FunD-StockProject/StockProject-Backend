package com.fund.stockProject.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.auth.repository.HttpCookieOAuth2AuthorizationRequestRepository;
import com.fund.stockProject.auth.repository.RefreshTokenRepository;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.auth.service.CustomOAuth2UserService;
import com.fund.stockProject.security.entrypoint.CustomAuthenticationEntryPoint;
import com.fund.stockProject.security.filter.CustomLogoutFilter;
import com.fund.stockProject.security.filter.JwtAuthenticationFilter;
import com.fund.stockProject.security.filter.JwtLoginFilter;
import com.fund.stockProject.security.handler.JwtLoginSuccessHandler;
import com.fund.stockProject.security.handler.LoginFailureHandler;
import com.fund.stockProject.security.handler.OAuth2LoginSuccessHandler;
import com.fund.stockProject.security.util.JwtUtil;
import com.fund.stockProject.security.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtUtil jwtUtil;
    private final CustomOAuth2UserService customOAuth2UserService;
    // 커스텀 핸들러들 직접 등록해줘야 함
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    //private final LogoutSuccessHandler logoutSuccessHandler;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DomainConfig domainConfig;
    private final ResponseUtil responseUtil;

    private static final String[] SWAGGER_API_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private static final String[] PUBLIC_API_PATHS = {
            "/error",
            "/favicon.ico",
            "/auth/register",
            "/auth/oauth2/register",
            "/auth/login",
            "/auth/reissue",
            "/auth/password-reset",
            "/auth/password-reset/confirm",
            "/auth/oauth2/naver",
            "/auth/oauth2/kakao",
            "/auth/oauth2/google",
            "/keyword/{keywordName}/stocks",
            "/keyword/popular/{country}",
            "/keyword/rankings",
            "/{id}/score/{country}",
            "/wordcloud/{symbol}/{country}",
            "/{id}/keywords",
            "/score/index",
            "/stock/search/{searchKeyword}/{country}",
            "/stock/autocomplete",
            "/stock/hot/{country}",
            "/stock/rising/{country}",
            "/stock/descent/{country}",
            "/stock/{id}/relevant",
            "/stock/{id}/chart/{country}",
            "/stock/{id}/info/{country}",
            "/stock/category/{category}/{country}",
            "/stock/rankings/hot",
            "/stock/summary/{symbol}/{country}"
    };

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // setCsrfRequestAttributeName(null)은 RequestAttributeHandler가 요청 속성에 CSRF 토큰을 노출하도록 합니다.
        // 이는 CsrfTokenRepository가 쿠키로 토큰을 설정하는 데 도움을 줍니다.
        requestHandler.setCsrfRequestAttributeName(null); // Spring Security 6.0+에서 토큰이 자동으로 쿠키로 전송되게 하는 설정

        http
                .csrf((csrf) -> csrf
                        .ignoringRequestMatchers(PUBLIC_API_PATHS)
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // ⭐ 이 부분이 중요!
                        .csrfTokenRequestHandler(requestHandler)); // ⭐ 이 부분도 중요!

        http
                .cors((cors) -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOrigins(List.of(domainConfig.getProd(), domainConfig.getTest()));
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
                        .requestMatchers(PUBLIC_API_PATHS).permitAll() // 이 경로들은 모두 인증 없이 접근 허용
                        .requestMatchers(SWAGGER_API_PATHS).permitAll() // Swagger 관련 경로는 모두 인증 없이 접근 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                );
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none()); // 세션 고정 방지 비활성화, JWT 기반 인증이므로

        http
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userRepository, responseUtil), LogoutFilter.class);
        // 커스텀 필터 등록
        JwtLoginFilter jwtLoginFilter = new JwtLoginFilter(authenticationManager(authenticationConfiguration));
        jwtLoginFilter.setAuthenticationSuccessHandler(jwtLoginSuccessHandler);
        jwtLoginFilter.setAuthenticationFailureHandler(loginFailureHandler);
        jwtLoginFilter.setFilterProcessesUrl("/auth/login");

        http
                .addFilterAt(jwtLoginFilter, UsernamePasswordAuthenticationFilter.class);

        http
                .addFilterAt(new CustomLogoutFilter(jwtUtil, refreshTokenRepository, responseUtil), LogoutFilter.class);

        http
                .oauth2Login((oauth2) -> oauth2
                        .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                                .userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                        )
                );

        http
                .logout((logout) -> logout
                        .logoutUrl("/auth/logout")
                        //.logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll());

        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
        );

        return http.build();
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
