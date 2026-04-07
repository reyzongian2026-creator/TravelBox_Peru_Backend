package com.tuempresa.storage.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.infrastructure.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm");

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/payments/confirm",
            "/api/v1/payments/checkout",
            "/api/v1/payments/process",
            "/api/v1/payments/intents",
            "/api/v1/payments/intent",
            "/api/v1/payments/one-click",
            "/api/v1/payments/cancellation-confirm");

    private static final long CLEANUP_EVERY_N_REQUESTS = 250;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxAuthRequestsPerWindow;
    private final int maxPaymentRequestsPerWindow;
    private final int windowSeconds;
    private final int blockSeconds;
    private final ConcurrentMap<String, AttemptWindow> attemptsByClient = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong();

    public AuthRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.auth.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.auth.max-requests-per-window:5}") int maxAuthRequestsPerWindow,
            @Value("${app.security.rate-limit.payments.max-requests-per-window:15}") int maxPaymentRequestsPerWindow,
            @Value("${app.security.rate-limit.auth.window-seconds:60}") int windowSeconds,
            @Value("${app.security.rate-limit.auth.block-seconds:300}") int blockSeconds) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxAuthRequestsPerWindow = Math.max(3, maxAuthRequestsPerWindow);
        this.maxPaymentRequestsPerWindow = Math.max(3, maxPaymentRequestsPerWindow);
        this.windowSeconds = Math.max(10, windowSeconds);
        this.blockSeconds = Math.max(30, blockSeconds);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (PROTECTED_PATHS.contains(path)) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        cleanupIfNeeded(nowEpochSeconds);

        String clientKey = buildClientKey(request);
        int limit = isPaymentPath(request.getRequestURI())
                ? maxPaymentRequestsPerWindow
                : maxAuthRequestsPerWindow;
        AttemptWindow window = attemptsByClient.computeIfAbsent(clientKey, ignored -> new AttemptWindow());
        long retryAfterSeconds = window.tryConsume(
                nowEpochSeconds,
                limit,
                windowSeconds,
                blockSeconds);

        if (retryAfterSeconds > 0) {
            writeRateLimitResponse(response, request.getRequestURI(), retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void cleanupIfNeeded(long nowEpochSeconds) {
        long seen = totalRequests.incrementAndGet();
        if (seen % CLEANUP_EVERY_N_REQUESTS != 0) {
            return;
        }
        long staleThreshold = nowEpochSeconds - (windowSeconds + blockSeconds + 60L);
        attemptsByClient.entrySet().removeIf(entry -> entry.getValue().isStale(staleThreshold));
    }

    private String buildClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor;
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        String endpoint = request.getRequestURI();
        return endpoint + "|" + (ip == null ? "unknown" : ip.trim());
    }

    private static boolean isPaymentPath(String path) {
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeRateLimitResponse(
            HttpServletResponse response,
            String path,
            long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(Math.max(1L, retryAfterSeconds)));

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMIT_EXCEEDED",
                "Demasiados intentos en autenticacion. Intenta nuevamente en unos minutos.",
                path,
                List.of("retryAfterSeconds=" + Math.max(1L, retryAfterSeconds)));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static final class AttemptWindow {

        private final Deque<Long> events = new ArrayDeque<>();
        private long blockedUntilEpochSeconds = 0L;
        private long lastSeenEpochSeconds = 0L;

        synchronized long tryConsume(
                long nowEpochSeconds,
                int maxRequestsPerWindow,
                int windowSeconds,
                int blockSeconds) {
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
