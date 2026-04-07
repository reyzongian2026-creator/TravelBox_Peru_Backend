package com.tuempresa.storage.shared.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtTokenProvider(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-minutes}") long accessMinutes,
            @Value("${app.security.jwt.refresh-token-days}") long refreshDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public String generateAccessToken(AuthUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessMinutes, ChronoUnit.MINUTES);
        Map<String, Object> claims = Map.of(
                "roles", principal.roleNames(),
                "uid", principal.getId(),
                "sv", principal.getSessionVersion());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getUsername())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(AuthUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshDays, ChronoUnit.DAYS);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Instant accessTokenExpiry() {
        return Instant.now().plus(accessMinutes, ChronoUnit.MINUTES);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshDays, ChronoUnit.DAYS);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public Set<String> extractRoleNames(String token) {
        Claims claims = parseClaims(token);
        Object rawRoles = claims.get("roles");
        if (rawRoles instanceof java.util.Collection<?> roles) {
            return roles.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        Object uid = claims.get("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public long extractSessionVersion(String token) {
        Claims claims = parseClaims(token);
        Object sv = claims.get("sv");
        if (sv instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
