package com.fund.stockProject.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "https://stockvalue13.netlify.app", "https://humanzipyo.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}