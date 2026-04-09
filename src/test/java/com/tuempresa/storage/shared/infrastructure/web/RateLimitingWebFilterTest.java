package com.tuempresa.storage.shared.infrastructure.web;

import com.tuempresa.storage.shared.infrastructure.security.RateLimiter;
import com.tuempresa.storage.shared.infrastructure.security.RateLimiter.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RateLimitingWebFilterTest {

    @Test
    void authenticatedProtectedRequestInvokesChainOnlyOnce() {
        RateLimiter rateLimiter = mock(RateLimiter.class);
        RateLimitingWebFilter filter = new RateLimitingWebFilter(rateLimiter, "https://www.inkavoy.pe");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments/confirm").build());
        AtomicInteger chainCalls = new AtomicInteger();
        WebFilterChain chain = ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        };

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("63", "n/a");
        authentication.setAuthenticated(true);

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block(Duration.ofSeconds(5));

        assertEquals(1, chainCalls.get());
        verify(rateLimiter).checkLimit("63", "/api/v1/payments/confirm", 30, Duration.ofMinutes(1));
    }

    @Test
    void unauthenticatedProtectedRequestFallsThroughOnceWithoutRateLimitCheck() {
        RateLimiter rateLimiter = mock(RateLimiter.class);
        RateLimitingWebFilter filter = new RateLimitingWebFilter(rateLimiter, "https://www.inkavoy.pe");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments/intents").build());
        AtomicInteger chainCalls = new AtomicInteger();
        WebFilterChain chain = ignored -> {
            chainCalls.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(1, chainCalls.get());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void rateLimitedResponseKeepsCorsHeadersForAllowedOrigin() {
        RateLimiter rateLimiter = mock(RateLimiter.class);
        doThrow(new RateLimitExceededException("too many"))
                .when(rateLimiter)
                .checkLimit("63", "/api/v1/payments/confirm", 30, Duration.ofMinutes(1));

        RateLimitingWebFilter filter = new RateLimitingWebFilter(rateLimiter, "https://www.inkavoy.pe");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments/confirm")
                        .header("Origin", "https://www.inkavoy.pe")
                        .build());

        TestingAuthenticationToken authentication = new TestingAuthenticationToken("63", "n/a");
        authentication.setAuthenticated(true);

        filter.filter(exchange, ignored -> Mono.empty())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block(Duration.ofSeconds(5));

        assertEquals(429, exchange.getResponse().getStatusCode().value());
        assertEquals("https://www.inkavoy.pe", exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
        assertEquals("true", exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Credentials"));
        assertTrue(exchange.getResponse().getHeaders().getFirst("Access-Control-Expose-Headers").contains("Retry-After"));
    }
}
