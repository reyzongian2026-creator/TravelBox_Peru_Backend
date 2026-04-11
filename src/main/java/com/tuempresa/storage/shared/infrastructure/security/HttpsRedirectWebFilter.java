package com.tuempresa.storage.shared.infrastructure.security;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Redirects plain HTTP requests to HTTPS when running in production.
 *
 * <p>Azure App Service terminates TLS at the edge and forwards the original
 * scheme via {@code X-Forwarded-Proto}. Spring's {@code forward-headers-strategy: framework}
 * setting makes {@link ServerWebExchange#getRequest()} reflect that header, so
 * checking {@code uri.getScheme()} here correctly detects the client-facing scheme.</p>
 *
 * <p>Health-check paths are excluded so Azure's HTTP-based liveness/readiness
 * probes continue to work without requiring HTTPS.</p>
 */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsRedirectWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        URI uri = exchange.getRequest().getURI();

        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return chain.filter(exchange);
        }

        // Allow Azure health probes over plain HTTP
        String path = uri.getPath();
        if (path != null && (path.startsWith("/actuator/health") || path.equals("/health"))) {
            return chain.filter(exchange);
        }

        URI httpsUri = UriComponentsBuilder.fromUri(uri)
                .scheme("https")
                .build(true)
                .toUri();

        exchange.getResponse().setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        exchange.getResponse().getHeaders().setLocation(httpsUri);
        return exchange.getResponse().setComplete();
    }
}
