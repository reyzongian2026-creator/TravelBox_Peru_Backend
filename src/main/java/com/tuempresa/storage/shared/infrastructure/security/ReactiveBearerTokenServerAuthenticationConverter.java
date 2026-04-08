package com.tuempresa.storage.shared.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveBearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String SSE_TOKEN_PREFIX = "SSE_RESOLVED:";
    private final SseTokenStore sseTokenStore;

    public ReactiveBearerTokenServerAuthenticationConverter(SseTokenStore sseTokenStore) {
        this.sseTokenStore = sseTokenStore;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = resolveBearerToken(exchange);
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
    }

    static boolean isSseResolved(String token) {
        return token != null && token.startsWith(SSE_TOKEN_PREFIX);
    }

    static String extractSseUsername(String token) {
        return token.substring(SSE_TOKEN_PREFIX.length());
    }

    private String resolveBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        String path = exchange.getRequest().getPath().value();
        if (!"/api/v1/notifications/events".equals(path) &&
                !"/api/v1/notifications/sse".equals(path)) {
            return null;
        }

        String accessToken = exchange.getRequest().getQueryParams().getFirst("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        // Resolve opaque SSE token to username
        String username = sseTokenStore.resolveUsername(accessToken);
        if (username != null) {
            return SSE_TOKEN_PREFIX + username;
        }
        // Fallback: return raw token so JWT auth manager can validate it
        return accessToken;
    }
}
