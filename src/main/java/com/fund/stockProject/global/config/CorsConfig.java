package com.fund.stockProject.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {
    private final DomainConfig domainConfig;

    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/**")
                .exposedHeaders("Set-Cookie")
                .allowedOrigins(domainConfig.getProd(), domainConfig.getTest()) // TODO : 실제 도메인으로 변경 필요
                .allowedHeaders("*")
                .allowedMethods("*")
                .allowCredentials(true);
    }
}