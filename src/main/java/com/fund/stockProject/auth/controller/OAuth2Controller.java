package com.fund.stockProject.auth.controller;

import com.fund.stockProject.auth.dto.TokensResponse;
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
    public ResponseEntity<TokensResponse> kakaoLogin(@RequestParam String code, @RequestParam String state) {
        try {
            TokensResponse tokens = oAuth2Service.kakaoLogin(code, state);
            return ResponseEntity.ok(tokens); // 200 OK
        } catch (NoSuchElementException e) { // 예를 들어 사용자가 없거나 특정 리소스를 못 찾았을 때
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 404 Not Found
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }

}