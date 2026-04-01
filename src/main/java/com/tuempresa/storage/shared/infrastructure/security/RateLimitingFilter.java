package com.tuempresa.storage.shared.infrastructure.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();
    private static final int REQUESTS_PER_MINUTE = 100;
    private static final int REQUESTS_PER_HOUR = 1000;
    private static final int ADMIN_REQUESTS_PER_MINUTE = 60;
    private static final int ADMIN_REQUESTS_PER_HOUR = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String key = clientIp + ":" + path;
        
        boolean isAdminEndpoint = path.startsWith("/api/v1/admin");
        Bucket bucket = BUCKETS.computeIfAbsent(key, k -> createBucket(isAdminEndpoint));
        
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
            );
        }
    }
    
    private Bucket createBucket(boolean isAdminEndpoint) {
        int perMinute = isAdminEndpoint ? ADMIN_REQUESTS_PER_MINUTE : REQUESTS_PER_MINUTE;
        int perHour = isAdminEndpoint ? ADMIN_REQUESTS_PER_HOUR : REQUESTS_PER_HOUR;
        
        Bandwidth perMinuteLimit = Bandwidth.builder()
            .capacity(perMinute)
            .refillGreedy(perMinute, Duration.ofMinutes(1))
            .build();
        Bandwidth perHourLimit = Bandwidth.builder()
            .capacity(perHour)
            .refillGreedy(perHour, Duration.ofHours(1))
            .build();
        
        return Bucket.builder()
            .addLimit(perMinuteLimit)
            .addLimit(perHourLimit)
            .build();
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }
}
