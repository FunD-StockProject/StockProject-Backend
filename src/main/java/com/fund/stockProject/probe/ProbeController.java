package com.fund.stockProject.probe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/probe")
public class ProbeController {

    private final WebClient webClient;

    @GetMapping("/slow")
    public ResponseEntity<String> slow() {
        String body = webClient.get()
                .uri("https://httpbin.org/delay/10")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(6))
                .block();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/big")
    public ResponseEntity<String> big() {
        String body = webClient.get()
                .uri("https://httpbin.org/bytes/3000000")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(6))
                .block();
        return ResponseEntity.ok(body);
    }
}


