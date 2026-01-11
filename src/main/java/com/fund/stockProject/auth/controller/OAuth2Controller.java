package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.LoginResponse;
import com.fund.stockProject.auth.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "소셜 OAuth2 로그인 엔드포인트 (카카오/네이버/구글/애플)")
public class OAuth2Controller {
    private final OAuth2Service oAuth2Service;

    @GetMapping("/login/kakao")
    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드(code)와 state 값을 받아 액세스/리프레시 토큰을 발급하거나 회원가입 필요 상태를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "404", description = "추가 회원가입 필요 (state=NEED_REGISTER)", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 OAuth2 연동 또는 서버 오류", content = @Content)
    })
    public ResponseEntity<LoginResponse> kakaoLogin(
            @Parameter(description = "인가 코드(Authorization Code)", example = "SplxlOBeZQQYbYS6WxSbIA") @RequestParam String code,
            @Parameter(description = "redirect uri", example = "http://localhost:5173/login/oauth2/code/kakao") @RequestParam String state) {
        try {
            log.info("Kakao login attempt - state: {}", state);
            LoginResponse response = oAuth2Service.kakaoLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                log.info("Kakao login - registration required");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            log.info("Kakao login successful");
            return ResponseEntity.ok(response); // 200 OK (로그인 성공)
        } catch (Exception e) {
            log.error("Kakao login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/login/naver")
    @Operation(summary = "네이버 로그인", description = "네이버 인가 코드 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원가입 필요", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 연동/서버 오류")
    })
    public ResponseEntity<LoginResponse> naverLogin(
            @Parameter(description = "인가 코드", example = "SplxlOBeZQQYbYS6WxSbIA") @RequestParam String code,
            @Parameter(description = "redirect uri", example = "http://localhost:5173/login/oauth2/code/naver") @RequestParam String state) {
        try {
            log.info("Naver login attempt - state: {}", state);
            LoginResponse response = oAuth2Service.naverLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                log.info("Naver login - registration required");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            log.info("Naver login successful");
            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            log.error("Naver login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

    @GetMapping("/login/google")
    @Operation(summary = "구글 로그인", description = "구글 인가 코드 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원가입 필요", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 연동/서버 오류")
    })
    public ResponseEntity<LoginResponse> googleLogin(
            @Parameter(description = "인가 코드", example = "4/0AY0e-g7...") @RequestParam String code,
            @Parameter(description = "redirect uri", example = "http://localhost:5173/login/oauth2/code/google") @RequestParam String state) {
        try {
            log.info("Google login attempt - state: {}", state);
            LoginResponse response = oAuth2Service.googleLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                log.info("Google login - registration required");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            log.info("Google login successful");
            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            log.error("Google login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

    @GetMapping("/login/apple")
    @Operation(summary = "애플 로그인", description = "애플 인가 코드 처리")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원가입 필요", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 연동/서버 오류")
    })
    public ResponseEntity<LoginResponse> appleLogin(
            @Parameter(description = "인가 코드", example = "c1d2e3f4...") @RequestParam String code,
            @Parameter(description = "redirect uri", example = "http://localhost:5173/login/oauth2/code/apple") @RequestParam String state) {
        try {
            log.info("Apple login attempt - state: {}", state);
            LoginResponse response = oAuth2Service.appleLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                log.info("Apple login - registration required");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            log.info("Apple login successful");
            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            log.error("Apple login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

    @PostMapping(value = "/login/apple", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "애플 로그인 (form_post)", description = "애플 form_post 응답을 처리합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "404", description = "회원가입 필요", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "500", description = "외부 연동/서버 오류")
    })
    public ResponseEntity<LoginResponse> appleLoginFormPost(
            @Parameter(description = "인가 코드", example = "c1d2e3f4...") @RequestParam String code,
            @Parameter(description = "redirect uri", example = "http://localhost:5173/login/oauth2/code/apple") @RequestParam String state,
            @Parameter(description = "user") @RequestParam(required = false) String user,
            @Parameter(description = "error", example = "invalid_request") @RequestParam(required = false) String error,
            @Parameter(description = "error_description") @RequestParam(required = false, name = "error_description") String errorDescription) {
        if (error != null && !error.isBlank()) {
            log.warn("Apple login form_post error: {} - {}", error, errorDescription);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            log.info("Apple login (form_post) attempt - state: {}", state);
            LoginResponse response = oAuth2Service.appleLogin(code, state, user);

            if ("NEED_REGISTER".equals(response.getState())) {
                log.info("Apple login (form_post) - registration required");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            log.info("Apple login (form_post) successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Apple login (form_post) failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
