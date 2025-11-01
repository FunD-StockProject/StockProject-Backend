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
                        .description("""
                                ## 인간지표 주식 투자 플랫폼 API 명세서
                                
                                ### 주요 기능
                                - **실험(모의 매수)**: 5영업일 기반 모의 매수/매도 실험 시스템
                                - **포트폴리오 분석**: 투자 패턴 및 인간지표 분석
                                - **주식 정보 조회**: 실시간 주가, 차트, 키워드 정보
                                - **인증**: JWT 기반 사용자 인증 및 OAuth2 소셜 로그인
                                
                                ### 인증 방식
                                모든 API는 JWT Bearer 토큰을 사용합니다. 
                                헤더에 `Authorization: Bearer {token}` 형식으로 전송하세요.
                                
                                ### 주의사항
                                - 모든 날짜는 영업일 기준으로 계산됩니다 (주말 및 공휴일 제외)
                                - 실험은 매수 후 5영업일 후 자동 매도됩니다
                                - ROI는 소수점 둘째 자리까지 표시됩니다
                                """)
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
                        new Tag().name("실험 (Experiment)").description("모의 매수/매도 실험 관련 API"),
                        new Tag().name("포트폴리오 (Portfolio)").description("투자 결과 및 분석 관련 API"),
                        new Tag().name("인증 (Auth)").description("사용자 인증 및 로그인 관련 API"),
                        new Tag().name("주식 (Stock)").description("주식 정보 조회 관련 API"),
                        new Tag().name("키워드 (Keyword)").description("주식 키워드 및 워드클라우드 관련 API"),
                        new Tag().name("알림 (Notification)").description("푸시 알림 및 SSE 관련 API")
                ))
                .externalDocs(new ExternalDocumentation()
                        .description("프론트엔드-백엔드 통합 가이드")
                        .url("https://github.com/FunD-StockProject/StockProject-Backend/blob/main/FRONTEND_BACKEND_INTEGRATION_GUIDE.md"));
    }
}
