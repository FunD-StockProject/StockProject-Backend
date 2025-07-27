package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.LoginResponse;
import com.fund.stockProject.auth.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class OAuth2Controller {
    private final OAuth2Service oAuth2Service;

    @GetMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@RequestParam String code, @RequestParam String state) {
        try {
            LoginResponse response = oAuth2Service.kakaoLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            return ResponseEntity.ok(response); // 200 OK (로그인 성공)
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/login/naver")
    public ResponseEntity<LoginResponse> naverLogin(@RequestParam String code, @RequestParam String state) {
        try {
            LoginResponse response = oAuth2Service.naverLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

    @GetMapping("/login/google")
    public ResponseEntity<LoginResponse> googleLogin(@RequestParam String code, @RequestParam String state) {
        try {
            LoginResponse response = oAuth2Service.googleLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

    @GetMapping("/login/apple")
    public ResponseEntity<LoginResponse> appleLogin(@RequestParam String code, @RequestParam String state) {
        try {
            LoginResponse response = oAuth2Service.appleLogin(code, state);

            if ("NEED_REGISTER".equals(response.getState())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response); // 404
            }

            return ResponseEntity.ok(response); // 200 OK
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

}