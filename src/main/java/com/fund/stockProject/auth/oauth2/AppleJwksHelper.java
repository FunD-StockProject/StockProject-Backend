package com.fund.stockProject.auth.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

@Component
public class AppleJwksHelper {

    private static final String APPLE_JWK_URL = "https://appleid.apple.com/auth/keys";

    public PublicKey getApplePublicKey(String idToken) {
        try {
            // 1. id_token의 헤더에서 kid 추출
            String[] parts = idToken.split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> header = mapper.readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");

            // 2. JWKS 받아오기
            InputStream is = new URL(APPLE_JWK_URL).openStream();
            JWKSet jwkSet = JWKSet.load(is);

            // 3. JWKS에서 kid에 맞는 공개키 찾기
            JWK jwk = jwkSet.getKeyByKeyId(kid);
            if (!(jwk instanceof RSAKey rsaKey)) {
                throw new IllegalArgumentException("Apple Public key with kid not found in JWKS");
            }

            // 4. java.security.PublicKey로 변환
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                    rsaKey.getModulus().decodeToBigInteger(),
                    rsaKey.getPublicExponent().decodeToBigInteger()
            );

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new RuntimeException("Apple 공개키 로딩 실패 : " + e.getMessage(), e);
        }
    }
}