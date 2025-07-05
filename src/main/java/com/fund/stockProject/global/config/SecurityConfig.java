package com.fund.stockProject.global.config;

import com.fund.stockProject.security.entrypoint.CustomAuthenticationEntryPoint;
import com.fund.stockProject.security.filter.JwtAuthenticationFilter;
import com.fund.stockProject.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CorsConfig corsConfig;

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
            "/auth/login/kakao",
            "/auth/login/naver",
            "/auth/oauth2/register",
            "/auth/login",
            "/auth/logout",
            "/auth/reissue",
            "/auth/find-email",
            "/auth/reset-password",
            "/auth/reset-password/confirm",
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
        http
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()));

        // 기본 설정 비활성화
        http
                .csrf(AbstractHttpConfigurer::disable); // CSRF 보호 비활성화 (JWT 사용 시 필요 없음)
        http
                .formLogin(AbstractHttpConfigurer::disable);
        http
                .httpBasic(AbstractHttpConfigurer::disable);

        // 경로 권한 설정
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(PUBLIC_API_PATHS).permitAll() // 이 경로들은 모두 인증 없이 접근 허용
                        .requestMatchers(SWAGGER_API_PATHS).permitAll() // Swagger 관련 경로는 모두 인증 없이 접근 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                );

        // 세션 설정
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none()); // 세션 고정 방지 비활성화, JWT 기반 인증이므로

        // JWT 검증 필터 등록
        http
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), LogoutFilter.class);

        // 로그인 예외 시 실행
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
        );

        return http.build();
    }
}
