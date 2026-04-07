package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.infrastructure.security.RateLimiter;
import com.tuempresa.storage.shared.infrastructure.security.RateLimiter.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Reactive {@link WebFilter} that enforces per-user rate limits on
 * security-sensitive payment endpoints.
 *
 * <p>Protected endpoints and their limits:</p>
 * <ul>
 *   <li>{@code /api/v1/payments/confirm} -- 10 attempts per minute</li>
 *   <li>{@code /api/v1/payments/intents} -- 20 attempts per minute</li>
 * </ul>
 *
 * <p>When a rate limit is exceeded the filter short-circuits the chain and
 * returns an HTTP 429 (Too Many Requests) response with a JSON error body.
 * Unauthenticated requests to protected endpoints are passed through without
 * rate-limit checks so that downstream security filters can handle
 * authentication concerns.</p>
 *
 * <p>This filter is ordered at {@code -1} so that it runs early in the
 * filter chain, before most application-level filters.</p>
 */
@Component
@Order(-1)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class RateLimitingWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingWebFilter.class);

    /** Sliding window duration used for all protected endpoints. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** JSON body returned when the rate limit is exceeded. */
    private static final String RATE_LIMIT_RESPONSE_BODY = """
            {"status":429,"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests. Try again later."}""";

    /**
     * Mapping of path prefixes to the maximum number of requests allowed
     * per user within the {@link #WINDOW}.
     */
    private static final Map<String, Integer> PROTECTED_ENDPOINTS = Map.of(
            "/api/v1/payments/confirm", 10,
            "/api/v1/payments/intents", 20
    );

    private final RateLimiter rateLimiter;

    /**
     * Creates a new {@code RateLimitingWebFilter}.
     *
     * @param rateLimiter the rate limiter used to track and enforce request quotas
     */
    public RateLimitingWebFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        log.info("RateLimitingWebFilter initialized with {} protected endpoint(s)",
                PROTECTED_ENDPOINTS.size());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * Intercepts incoming requests and applies rate limiting to protected
     * payment endpoints.
     *
     * @param exchange the current server exchange
     * @param chain    the remaining filter chain
     * @return a {@link Mono} that completes when the exchange has been handled
     */
    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Determine whether this request targets a protected endpoint
        Integer maxAttempts = resolveMaxAttempts(path);
        if (maxAttempts == null) {
            return chain.filter(exchange);
        }

        // Retrieve the authenticated principal from the reactive security context
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .flatMap(userId -> applyRateLimit(exchange, chain, userId, path, maxAttempts))
                .switchIfEmpty(chain.filter(exchange));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Resolves the maximum number of attempts allowed for the given request
     * path. Returns {@code null} if the path does not match any protected
     * endpoint.
     */
    private Integer resolveMaxAttempts(String path) {
        for (Map.Entry<String, Integer> entry : PROTECTED_ENDPOINTS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Applies the rate limit check for an authenticated user. On success the
     * request is forwarded down the filter chain; on violation a 429 response
     * is written directly.
     */
    private Mono<Void> applyRateLimit(ServerWebExchange exchange,
                                      WebFilterChain chain,
                                      String userId,
                                      String endpoint,
                                      int maxAttempts) {
        try {
            rateLimiter.checkLimit(userId, endpoint, maxAttempts, WINDOW);
            return chain.filter(exchange);
        } catch (RateLimitExceededException ex) {
            log.warn("Rate limit hit: userId='{}', endpoint='{}', max={}/{}",
                    userId, endpoint, maxAttempts, WINDOW);
            return writeTooManyRequestsResponse(exchange);
        }
    }

    /**
     * Writes an HTTP 429 JSON error response and completes the exchange
     * without forwarding to downstream filters or handlers.
     */
    private Mono<Void> writeTooManyRequestsResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes = RATE_LIMIT_RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
