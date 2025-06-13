package com.fund.stockProject.security.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResponseUtil {
    private final ObjectMapper objectMapper;

    public void sendJsonResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, String> responseBody = Map.of("message", message);
        objectMapper.writeValue(response.getWriter(), responseBody);
    }

    // 성공 응답 (200 OK)
    public void sendSuccessResponse(HttpServletResponse response, String message) throws IOException {
        sendJsonResponse(response, HttpStatus.OK, message);
    }

    // 에러 응답
    public void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        sendJsonResponse(response, status, message);
    }
}

