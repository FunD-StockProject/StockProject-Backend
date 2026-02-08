package com.fund.stockProject.global.config;

import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.security.entrypoint.CustomAuthenticationEntryPoint;
import com.fund.stockProject.security.filter.JwtAuthenticationFilter;
import com.fund.stockProject.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtil jwtUtil;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CorsConfig corsConfig;
    private final UserRepository userRepository;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
            "/auth/register/local",
            "/auth/login",
            "/auth/login/local",
            "/auth/login/kakao",
            "/auth/login/naver",
            "/auth/login/google",
            "/auth/login/apple",
            "/auth/oauth2/register",
            "/auth/logout",
            "/auth/reissue",
            "/auth/find-email",
            "/auth/nickname",
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
            "/stock/summary/{symbol}/{country}",
            "/stock/sector/average/{country}",
            "/stock/{id}/sector/percentile",
            "/stock/{id}/average/month",
            "/stock/sector/average/{country}/{sector}",
    };

    @Bean
    public AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(4);
        delegate.setMaxPoolSize(8);
        delegate.setQueueCapacity(100);
        delegate.initialize();
        // 작업 실행 시 SecurityContext를 복사/복원
        return new DelegatingSecurityContextAsyncTaskExecutor(delegate);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(PUBLIC_API_PATHS).permitAll()
                        .requestMatchers(SWAGGER_API_PATHS).permitAll()
                        .anyRequest().authenticated()
                );
        // 부분 stateful: 필요 시 세션 생성 & SecurityContext 자동 저장
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation().migrateSession())
                .securityContext(sc -> sc.requireExplicitSave(false));
        http
                .addFilterBefore(jwtAuthenticationFilter, LogoutFilter.class);
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
        );
        return http.build();
    }
}
