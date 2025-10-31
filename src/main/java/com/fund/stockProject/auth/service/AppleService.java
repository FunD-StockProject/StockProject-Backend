package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.dto.AppleTokenResponse;
import com.fund.stockProject.auth.oauth2.AppleJwksHelper;
import com.fund.stockProject.auth.oauth2.AppleOAuth2UserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AppleService {

    @Value("${spring.security.oauth2.apple.team-id}")
    private String teamId;
    @Value("${spring.security.oauth2.client.registration.apple.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.apple.key-id}")
    private String keyId;
    @Value("${spring.security.oauth2.apple.private-key-path}")
    private String privateKeyPath;
    private final AppleJwksHelper appleJwksHelper;
    private final RestTemplate restTemplate;

    public AppleTokenResponse getAccessToken(String code, String redirectUri) {
        String clientSecret = generateClientSecret();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<AppleTokenResponse> response =
                restTemplate.postForEntity("https://appleid.apple.com/auth/token", request, AppleTokenResponse.class);

        return response.getBody();
    }

    public AppleOAuth2UserInfo getUserInfoFromIdToken(String idToken) {
        PublicKey applePublicKey = appleJwksHelper.getApplePublicKey(idToken); // kid 기반으로 JWKS에서 가져오는 헬퍼
        Jws<Claims> jwsClaims = Jwts.parser() // TODO: parserBuilder로 변경
                .setSigningKey(applePublicKey)
                .build()
                .parseClaimsJws(idToken);
        Claims claims = jwsClaims.getBody();
        return new AppleOAuth2UserInfo(claims);
    }

    private String generateClientSecret() {
        try {
            String privateKeyStr = new String(Files.readAllBytes(Paths.get(privateKeyPath)), StandardCharsets.UTF_8);
            String pkcs8PEM = privateKeyStr.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pkcs8PEM);

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            long now = System.currentTimeMillis();
            Date nowDate = new Date(now);
            Date untilDate = new Date(now + 60 * 60 * 1000);

            return Jwts.builder()
                    .setHeaderParam("kid", keyId)
                    .setIssuer(teamId)
                    .setAudience("https://appleid.apple.com")
                    .setSubject(clientId)
                    .setIssuedAt(nowDate)
                    .setExpiration(untilDate)
                    .signWith(privateKey, SignatureAlgorithm.ES256)
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("애플 client_secret (JWT) 생성 실패", e);
        }
    }
}