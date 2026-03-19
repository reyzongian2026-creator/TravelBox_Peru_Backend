package com.tuempresa.storage.shared.infrastructure.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class FirebaseBearerTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = resolveBearerToken(exchange);
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return Mono.just(new UsernamePasswordAuthenticationToken("firebase", token));
    }

    private String resolveBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        String path = exchange.getRequest().getPath().value();
        if (!"/api/v1/notifications/events".equals(path) && !"/api/v1/notifications/sse".equals(path)) {
            return null;
        }

        String accessToken = exchange.getRequest().getQueryParams().getFirst("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        if (accessToken.startsWith("Bearer ")) {
            return accessToken.substring(7);
        }
        return accessToken;
    }
}
