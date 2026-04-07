package com.tuempresa.storage.shared.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private RateLimiter.RateLimitViolationRepository violationRepository;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(violationRepository);
    }

    @Test
    void shouldTrackAttemptsUntilLimitIsExceeded() {
        Duration window = Duration.ofMinutes(1);

        rateLimiter.checkLimit("user@example.com", "/payments/confirm", 2, window);
        rateLimiter.checkLimit("user@example.com", "/payments/confirm", 2, window);

        RateLimiter.RateLimitExceededException ex = assertThrows(
                RateLimiter.RateLimitExceededException.class,
                () -> rateLimiter.checkLimit("user@example.com", "/payments/confirm", 2, window)
        );

        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
        assertEquals(2, rateLimiter.getCurrentAttemptCount("user@example.com", "/payments/confirm", window));
        verify(violationRepository).recordViolation(
                eq("user@example.com"),
                eq("/payments/confirm"),
                eq(2),
                eq(2),
                eq(window),
                any()
        );
    }

    @Test
    void shouldResetTrackedAttempts() {
        Duration window = Duration.ofMinutes(1);

        rateLimiter.checkLimit("user@example.com", "/payments/intents", 3, window);
        rateLimiter.checkLimit("user@example.com", "/payments/intents", 3, window);
        assertEquals(2, rateLimiter.getCurrentAttemptCount("user@example.com", "/payments/intents", window));

        rateLimiter.resetLimit("user@example.com", "/payments/intents");

        assertEquals(0, rateLimiter.getCurrentAttemptCount("user@example.com", "/payments/intents", window));
        verify(violationRepository, never()).recordViolation(any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void shouldRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class,
                () -> rateLimiter.checkLimit(" ", "/payments/confirm", 1, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> rateLimiter.checkLimit("user@example.com", " ", 1, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> rateLimiter.checkLimit("user@example.com", "/payments/confirm", 0, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class,
                () -> rateLimiter.checkLimit("user@example.com", "/payments/confirm", 1, Duration.ZERO));
    }

    @Test
    void shouldStillEnforceLimitWhenViolationPersistenceFails() {
        Duration window = Duration.ofMinutes(1);
        doThrow(new RuntimeException("db down"))
                .when(violationRepository)
                .recordViolation(any(), any(), anyInt(), anyInt(), any(), any());

        rateLimiter.checkLimit("user@example.com", "/payments/confirm", 1, window);

        assertThrows(
                RateLimiter.RateLimitExceededException.class,
                () -> rateLimiter.checkLimit("user@example.com", "/payments/confirm", 1, window)
        );

        verify(violationRepository).recordViolation(
                eq("user@example.com"),
                eq("/payments/confirm"),
                eq(1),
                eq(1),
                eq(window),
                any()
        );
    }
}
