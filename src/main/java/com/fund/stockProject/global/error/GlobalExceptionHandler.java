package com.fund.stockProject.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<String> handleTimeout(TimeoutException e) {
        log.warn("Gateway timeout from upstream: {}", e.toString());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Upstream timeout");
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<String> handleWebClient(WebClientResponseException e) {
        log.warn("Upstream HTTP error: {} {}", e.getRawStatusCode(), e.getStatusText());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(new MediaType("text","plain", StandardCharsets.UTF_8))
                .body("Upstream error");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException e) {
        if (Exceptions.isRetryExhausted(e)) {
            log.warn("Retry exhausted: {}", e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Retry exhausted");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
    }
}


