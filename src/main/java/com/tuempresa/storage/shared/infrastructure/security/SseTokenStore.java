package com.tuempresa.storage.shared.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SseTokenStore {

    private static final long TOKEN_TTL_SECONDS = 120;
    private static final long CLEANUP_INTERVAL_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentMap<String, SseTokenEntry> tokens = new ConcurrentHashMap<>();
    private volatile long lastCleanup = Instant.now().getEpochSecond();

    public String issueToken(String username) {
        cleanupIfNeeded();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new SseTokenEntry(username, Instant.now().getEpochSecond() + TOKEN_TTL_SECONDS));
        return token;
    }

    public String resolveUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        SseTokenEntry entry = tokens.get(token);
        if (entry == null) {
            return null;
        }
        if (Instant.now().getEpochSecond() > entry.expiresAt()) {
            tokens.remove(token);
            return null;
        }
        return entry.username();
    }

    private void cleanupIfNeeded() {
        long now = Instant.now().getEpochSecond();
        if (now - lastCleanup < CLEANUP_INTERVAL_SECONDS) {
            return;
        }
        lastCleanup = now;
        tokens.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    private record SseTokenEntry(String username, long expiresAt) {
    }
}
