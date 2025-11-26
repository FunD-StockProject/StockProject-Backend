package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.*;
import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.auth.service.TokenService;
import com.fund.stockProject.security.principle.CustomUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.Map;

@Tag(name = "인증 (Auth)", description = "인증 및 회원 관리 API (회원가입, 토큰 재발급, 로그아웃, 중복 체크 등)")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "소셜 회원가입", description = "신규 사용자를 등록하고 프로필 이미지를 업로드합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 값", content = @Content),
            @ApiResponse(responseCode = "409", description = "닉네임 또는 이메일 중복", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> register(
            @Parameter(description = "소셜 회원가입 요청 DTO", required = true)
            @ModelAttribute OAuth2RegisterRequest request) {
        try {
            String imageUrl = authService.register(request);
            return ResponseEntity.ok(Map.of(
                    "message", "User registered successfully",
                    "profileImageUrl", imageUrl != null ? imageUrl : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete social registration: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/register/local", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "일반 회원가입", description = "이메일과 비밀번호로 신규 사용자를 등록하고 프로필 이미지를 업로드합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 값", content = @Content),
            @ApiResponse(responseCode = "409", description = "닉네임 또는 이메일 중복", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    public ResponseEntity<Map<String, String>> registerLocal(
            @Parameter(description = "사용자 이메일", required = true) @RequestParam String email,
            @Parameter(description = "비밀번호", required = true) @RequestParam String password,
            @Parameter(description = "사용자 닉네임", required = true) @RequestParam String nickname,
            @Parameter(description = "생년월일 (yyyy-MM-dd)") @RequestParam(required = false) String birthDate,
            @Parameter(description = "마케팅 정보 수신 동의 여부") @RequestParam(required = false) Boolean marketingAgreement,
            @Parameter(description = "프로필 이미지 파일") @RequestPart(required = false) MultipartFile image) {
        try {
            // Validation
            if (email == null || email.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "이메일은 필수입니다"));
            }
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "유효한 이메일 형식이 아닙니다"));
            }
            if (password == null || password.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "비밀번호는 필수입니다"));
            }
            if (password.length() < 8) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "비밀번호는 최소 8자 이상이어야 합니다"));
            }
            if (nickname == null || nickname.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "닉네임은 필수입니다"));
            }

            // LocalRegisterRequest 생성
            LocalRegisterRequest request = new LocalRegisterRequest();
            request.setEmail(email);
            request.setPassword(password);
            request.setNickname(nickname);
            if (birthDate != null && !birthDate.isBlank()) {
                try {
                    request.setBirthDate(java.time.LocalDate.parse(birthDate));
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }
            request.setMarketingAgreement(marketingAgreement);
            request.setImage(image);
            
            String imageUrl = authService.registerLocal(request);
            return ResponseEntity.ok(Map.of(
                    "message", "User registered successfully",
                    "profileImageUrl", imageUrl != null ? imageUrl : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete registration: " + e.getMessage()));
        }
    }

    @PostMapping("/login/local")
    @Operation(summary = "일반 로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않음", content = @Content),
            @ApiResponse(responseCode = "400", description = "잘못된 입력 값", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content)
    })
    public ResponseEntity<LoginResponse> loginLocal(
            @Parameter(description = "일반 로그인 요청 DTO", required = true)
            @Valid @RequestBody LocalLoginRequest request) {
        try {
            LoginResponse response = authService.loginLocal(request);
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("FAILED", null, null, null, null, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LoginResponse("FAILED", null, null, null, null, null));
        }
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴", description = "현재 인증된 사용자를 영구 삭제 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "500", description = "탈퇴 처리 중 오류")
    })
    public ResponseEntity<Map<String, String>> withdrawUser(@AuthenticationPrincipal @Parameter(description = "인증된 사용자 정보") CustomUserDetails customUserDetails) {
        if (customUserDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // HTTP 401 Unauthorized
                    .body(Map.of("message", "Authentication required"));
        }

        try {
            authService.withdrawUser(customUserDetails.getEmail());
            return ResponseEntity.ok(Map.of("message", "User withdrawn successfully")); // HTTP 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to withdraw user: " + e.getMessage()));
        }
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access 토큰 재발급", description = "Access 토큰을 Refresh 토큰을 이용해 재발급합니다. Refresh 토큰도 갱신됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 토큰 형식 / 요청"),
            @ApiResponse(responseCode = "403", description = "Refresh 토큰 만료"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<LoginResponse> reissue(
            @RequestBody @Parameter(description = "Refresh 토큰 요청", required = true) RefreshTokenRequest request) {
        try {
            LoginResponse response = tokenService.reissueTokens(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e instanceof ExpiredJwtException || e.getMessage().contains("expired")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new LoginResponse("FAILED", null, null, null, null, null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse("FAILED", null, null, null, null, null));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "서버에 보관된 Refresh 토큰을 무효화하여 이후 재발급을 차단합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "로그아웃 실패 (토큰 검증 실패 등)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<?> logout(
            @RequestBody @Parameter(description = "Refresh 토큰 DTO", required = true) RefreshTokenRequest requestDto) {
        try {
            tokenService.logout(requestDto.getRefreshToken());
            return ResponseEntity.ok(Map.of("message", "Logout successful"));
        } catch (Exception e) {
            // 예를 들어 유효하지 않은 토큰 포맷 등의 이유로 실패했을 때
            return ResponseEntity.badRequest().body(Map.of("message", "Logout failed: " + e.getMessage()));
        }
    }

    @GetMapping("/nickname")
    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임이 이미 사용 중인지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 파라미터")
    })
    public ResponseEntity<Map<String, Boolean>> checkNicknameDuplicate(
            @Parameter(description = "중복 확인할 닉네임", example = "human123", required = true)
            @RequestParam String nickname) {
        boolean isDuplicate = authService.isNicknameDuplicate(nickname);
        return ResponseEntity.ok(Map.of("duplicate", isDuplicate));
    }

    @GetMapping("/email")
    @Operation(summary = "이메일 중복 확인", description = "입력한 이메일이 이미 사용 중인지 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 파라미터")
    })
    public ResponseEntity<Map<String, Boolean>> checkEmailDuplicate(
            @Parameter(description = "중복 확인할 이메일", example = "user@example.com", required = true)
            @RequestParam String email) {
        boolean isDuplicate = authService.isEmailDuplicate(email);
        return ResponseEntity.ok(Map.of("duplicate", isDuplicate));
    }
}
