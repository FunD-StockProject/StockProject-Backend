package com.fund.stockProject.auth.service;

import com.fund.stockProject.auth.domain.PROVIDER;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AppleLoginContextService {

    private static final String FALLBACK_PREFIX = "apple_";
    private static final String FALLBACK_DOMAIN = "@apple.local";
    private static final long PENDING_TTL_MILLIS = Duration.ofMinutes(30).toMillis();

    private final ConcurrentMap<String, PendingProviderId> pendingProviderIdsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingProviderId> pendingProviderIdsByClient = new ConcurrentHashMap<>();

    public void savePendingProviderId(PROVIDER provider, String email, String providerId) {
        String normalizedEmail = normalizeEmail(email);
        String scopedKey = buildScopedKey(provider, normalizedEmail);
        if (scopedKey == null || isBlank(providerId)) {
            return;
        }
        cleanupExpiredIfNeeded();
        pendingProviderIdsByEmail.put(
                scopedKey,
                new PendingProviderId(providerId.trim(), System.currentTimeMillis() + PENDING_TTL_MILLIS)
        );
    }

    public Optional<String> consumePendingProviderId(PROVIDER provider, String email) {
        String normalizedEmail = normalizeEmail(email);
        String scopedKey = buildScopedKey(provider, normalizedEmail);
        if (scopedKey == null) {
            return Optional.empty();
        }

        PendingProviderId pending = pendingProviderIdsByEmail.remove(scopedKey);
        if (pending == null || pending.expiresAtMillis < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(pending.providerId);
    }

    public void savePendingProviderIdByClient(PROVIDER provider, String clientKey, String providerId) {
        String normalizedClientKey = normalizeClientKey(clientKey);
        String scopedKey = buildScopedKey(provider, normalizedClientKey);
        if (scopedKey == null || isBlank(providerId)) {
            return;
        }
        cleanupExpiredIfNeeded();
        pendingProviderIdsByClient.put(
                scopedKey,
                new PendingProviderId(providerId.trim(), System.currentTimeMillis() + PENDING_TTL_MILLIS)
        );
    }

    public Optional<String> consumePendingProviderIdByClient(PROVIDER provider, String clientKey) {
        String normalizedClientKey = normalizeClientKey(clientKey);
        String scopedKey = buildScopedKey(provider, normalizedClientKey);
        if (scopedKey == null) {
            return Optional.empty();
        }

        PendingProviderId pending = pendingProviderIdsByClient.remove(scopedKey);
        if (pending == null || pending.expiresAtMillis < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(pending.providerId);
    }

    public String buildFallbackEmail(String providerId) {
        if (isBlank(providerId)) {
            throw new IllegalArgumentException("Apple providerId is required to build fallback email");
        }
        return FALLBACK_PREFIX + encodeHex(providerId.trim()) + FALLBACK_DOMAIN;
    }

    public Optional<String> extractProviderIdFromFallbackEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || !normalizedEmail.endsWith(FALLBACK_DOMAIN)) {
            return Optional.empty();
        }
        String localPart = normalizedEmail.substring(0, normalizedEmail.length() - FALLBACK_DOMAIN.length());
        if (!localPart.startsWith(FALLBACK_PREFIX)) {
            return Optional.empty();
        }

        String encoded = localPart.substring(FALLBACK_PREFIX.length());
        if (encoded.isEmpty() || encoded.length() % 2 != 0) {
            return Optional.empty();
        }

        try {
            return Optional.of(decodeHex(encoded));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void cleanupExpiredIfNeeded() {
        if (pendingProviderIdsByEmail.size() + pendingProviderIdsByClient.size() < 1000) {
            return;
        }
        long now = System.currentTimeMillis();
        pendingProviderIdsByEmail.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
        pendingProviderIdsByClient.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis < now);
    }

    private String normalizeEmail(String email) {
        if (isBlank(email)) {
            return null;
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeClientKey(String clientKey) {
        if (isBlank(clientKey)) {
            return null;
        }
        return clientKey.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String buildScopedKey(PROVIDER provider, String key) {
        if (provider == null || key == null) {
            return null;
        }
        return provider.name() + "|" + key;
    }

    private String encodeHex(String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private String decodeHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex string");
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    private record PendingProviderId(String providerId, long expiresAtMillis) {
    }
}
