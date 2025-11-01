package com.fund.stockProject.global.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        name = "bearerAuth",                  // 인증 스키마 이름(아래에서 refer)
        type = SecuritySchemeType.HTTP,       // HTTP 인증타입
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("인간지표 Stock API")
                        .description("인간지표 API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("인간지표 개발팀")
                                .email("support@humanzipyo.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://humanzipyo.com")))
                .servers(List.of(
                        new Server()
                                .url("https://api.humanzipyo.com")
                                .description("프로덕션 서버"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버")
                ))
                .tags(List.of(
                        new Tag().name("실험 (Experiment)"),
                        new Tag().name("포트폴리오 (Portfolio)"),
                        new Tag().name("인증 (Auth)"),
                        new Tag().name("주식 (Stock)"),
                        new Tag().name("키워드 (Keyword)"),
                        new Tag().name("알림 (Notification)")
                ));
    }
}
