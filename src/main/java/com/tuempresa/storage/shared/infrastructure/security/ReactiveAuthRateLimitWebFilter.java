package com.tuempresa.storage.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.infrastructure.web.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveAuthRateLimitWebFilter implements WebFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm"
    );

    private static final long CLEANUP_EVERY_N_REQUESTS = 250;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRequestsPerWindow;
    private final int windowSeconds;
    private final int blockSeconds;
    private final ConcurrentMap<String, AttemptWindow> attemptsByClient = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong();

    public ReactiveAuthRateLimitWebFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.auth.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.auth.max-requests-per-window:12}") int maxRequestsPerWindow,
            @Value("${app.security.rate-limit.auth.window-seconds:60}") int windowSeconds,
            @Value("${app.security.rate-limit.auth.block-seconds:300}") int blockSeconds
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRequestsPerWindow = Math.max(3, maxRequestsPerWindow);
        this.windowSeconds = Math.max(10, windowSeconds);
        this.blockSeconds = Math.max(30, blockSeconds);
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!PROTECTED_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        cleanupIfNeeded(nowEpochSeconds);

        String clientKey = buildClientKey(exchange, path);
        AttemptWindow window = attemptsByClient.computeIfAbsent(clientKey, ignored -> new AttemptWindow());
        long retryAfterSeconds = window.tryConsume(nowEpochSeconds, maxRequestsPerWindow, windowSeconds, blockSeconds);
        if (retryAfterSeconds <= 0) {
            return chain.filter(exchange);
        }
        return writeRateLimitResponse(exchange, path, retryAfterSeconds);
    }

    private void cleanupIfNeeded(long nowEpochSeconds) {
        long seen = totalRequests.incrementAndGet();
        if (seen % CLEANUP_EVERY_N_REQUESTS != 0) {
            return;
        }
        long staleThreshold = nowEpochSeconds - (windowSeconds + blockSeconds + 60L);
        attemptsByClient.entrySet().removeIf(entry -> entry.getValue().isStale(staleThreshold));
    }

    private String buildClientKey(ServerWebExchange exchange, String endpoint) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip = forwardedFor;
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        if (ip != null) {
            ip = ip.trim();
        }
        if (ip == null || ip.isBlank()) {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null) {
                if (remoteAddress.getAddress() != null) {
                    ip = remoteAddress.getAddress().getHostAddress();
                } else if (remoteAddress.getHostString() != null && !remoteAddress.getHostString().isBlank()) {
                    ip = remoteAddress.getHostString();
                }
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        return endpoint + "|" + ip.trim();
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, String path, long retryAfterSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", Long.toString(Math.max(1L, retryAfterSeconds)));
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMIT_EXCEEDED",
                "Demasiados intentos en autenticacion. Intenta nuevamente en unos minutos.",
                path,
                List.of("retryAfterSeconds=" + Math.max(1L, retryAfterSeconds))
        );
        try {
            byte[] payload = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
        } catch (Exception ex) {
            return response.setComplete();
        }
    }

    private static final class AttemptWindow {

        private final Deque<Long> events = new ArrayDeque<>();
        private long blockedUntilEpochSeconds = 0L;
        private long lastSeenEpochSeconds = 0L;

        synchronized long tryConsume(
                long nowEpochSeconds,
                int maxRequestsPerWindow,
                int windowSeconds,
                int blockSeconds
        ) {
            lastSeenEpochSeconds = nowEpochSeconds;
            if (blockedUntilEpochSeconds > nowEpochSeconds) {
                return blockedUntilEpochSeconds - nowEpochSeconds;
            }
            long floor = nowEpochSeconds - windowSeconds;
            while (!events.isEmpty() && events.peekFirst() <= floor) {
                events.pollFirst();
            }
            if (events.size() >= maxRequestsPerWindow) {
                events.clear();
                blockedUntilEpochSeconds = nowEpochSeconds + blockSeconds;
                return blockSeconds;
            }
            events.addLast(nowEpochSeconds);
            return 0L;
        }

        synchronized boolean isStale(long staleThresholdEpochSeconds) {
            if (blockedUntilEpochSeconds >= staleThresholdEpochSeconds) {
                return false;
            }
            return events.isEmpty() && lastSeenEpochSeconds < staleThresholdEpochSeconds;
        }
    }
}
