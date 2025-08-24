package com.fund.stockProject.notification.controller;

import com.fund.stockProject.notification.service.SsePushService;
import com.fund.stockProject.security.util.JwtUtil;
import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification", description = "SSE 실시간 푸시 스트림 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SsePushController { // public 으로 변경 (Swagger 스캔 용이)
    private final SsePushService sse;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    @GetMapping(value = "/notification/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "알림 SSE 구독", description = "Authorization 헤더(Bearer 토큰)를 이용해 SSE 연결을 수립하고 실시간 알림을 수신합니다.\n" +
            "이 엔드포인트는 지속 연결을 유지하며 Heartbeat 또는 신규 알림 이벤트를 전송합니다.\n" +
            "클라이언트는 토큰 갱신 시 재연결해야 하며, 네트워크 단절 시 지수적 backoff 재시도 전략을 권장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 스트림 시작", content = @Content(mediaType = "text/event-stream", schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 또는 누락된 토큰"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public SseEmitter stream(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        try {
            String token = extractToken(authorizationHeader); // Bearer 제거 후 순수 JWT
            String email = jwtUtil.getEmail(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
            return sse.register(user.getId());
        } catch (ResponseStatusException e) {
            throw e; // 그대로 전파
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: " + e.getMessage());
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header missing");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header must start with 'Bearer '");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Empty bearer token");
        }
        return token;
    }
}
