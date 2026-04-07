package com.tuempresa.storage.shared.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * In-memory, sliding-window rate limiter backed by a Caffeine cache.
 *
 * <p>Each (userId, endpoint) pair maintains an ordered list of attempt
 * timestamps. When {@link #checkLimit} is called, expired entries outside
 * the window are pruned and the remaining count is compared against the
 * allowed maximum. Violations are persisted via the injected
 * {@link RateLimitViolationRepository} for auditing purposes.</p>
 *
 * <p>This component is designed for single-instance deployments. For
 * distributed rate limiting, consider a Redis-backed implementation.</p>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Maximum time entries are kept in the cache before automatic eviction.
     * This is a ceiling; the actual sliding window may be shorter.
     */
    private static final long MAX_CACHE_ENTRY_TTL_HOURS = 24;

    /** Maximum number of distinct (userId+endpoint) keys retained in the cache. */
    private static final long MAX_CACHE_SIZE = 100_000;

    private final Cache<String, CopyOnWriteArrayList<Instant>> attemptsCache;
    private final RateLimitViolationRepository violationRepository;

    /**
     * Creates a new {@code RateLimiter}.
     *
     * @param violationRepository repository used to persist rate-limit violations
     */
    public RateLimiter(RateLimitViolationRepository violationRepository) {
        this.violationRepository = violationRepository;
        this.attemptsCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(MAX_CACHE_ENTRY_TTL_HOURS, TimeUnit.HOURS)
                .build();

        log.info("RateLimiter initialized (maxCacheSize={}, entryTTL={}h)",
                MAX_CACHE_SIZE, MAX_CACHE_ENTRY_TTL_HOURS);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether the given user has exceeded the allowed number of attempts
     * for the specified endpoint within the sliding window.
     *
     * <p>If the limit is exceeded a {@link RateLimitExceededException} is thrown
     * and the violation is recorded in the database.</p>
     *
     * @param userId         the identifier of the user making the request
     * @param endpoint       a logical name for the endpoint being rate-limited
     * @param maxAttempts    the maximum number of allowed attempts in the window
     * @param windowDuration the length of the sliding time window
     * @throws RateLimitExceededException if the user has exceeded the limit
     * @throws IllegalArgumentException   if any required parameter is invalid
     */
    public void checkLimit(String userId, String endpoint,
                           int maxAttempts, Duration windowDuration) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be null or blank");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (windowDuration == null || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("windowDuration must be a positive duration");
        }

        String cacheKey = buildCacheKey(userId, endpoint);
        Instant now = Instant.now();
        Instant windowStart = now.minus(windowDuration);

        CopyOnWriteArrayList<Instant> attempts = attemptsCache.get(
                cacheKey, k -> new CopyOnWriteArrayList<>());

        // Prune entries outside the sliding window
        attempts.removeIf(ts -> ts.isBefore(windowStart));

        if (attempts.size() >= maxAttempts) {
            log.warn("Rate limit exceeded for userId='{}', endpoint='{}' " +
                            "({} attempts in last {})",
                    userId, endpoint, attempts.size(), windowDuration);

            recordViolation(userId, endpoint, attempts.size(), maxAttempts, windowDuration);

            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded for user '%s' on endpoint '%s': " +
                                    "%d attempts in the last %s (max %d allowed)",
                            userId, endpoint, attempts.size(),
                            formatDuration(windowDuration), maxAttempts));
        }

        // Record this attempt
        attempts.add(now);
        log.debug("Attempt recorded for userId='{}', endpoint='{}' ({}/{})",
                userId, endpoint, attempts.size(), maxAttempts);
    }

    /**
     * Resets the attempt counter for a given user and endpoint.
     * Useful after a successful authentication or administrative override.
     *
     * @param userId   the user whose counter should be reset
     * @param endpoint the endpoint to reset
     */
    public void resetLimit(String userId, String endpoint) {
        String cacheKey = buildCacheKey(userId, endpoint);
        attemptsCache.invalidate(cacheKey);
        log.info("Rate limit reset for userId='{}', endpoint='{}'", userId, endpoint);
    }

    /**
     * Returns the current number of attempts tracked for a user/endpoint pair
     * within the given window. Does not count as an attempt itself.
     *
     * @param userId         the user identifier
     * @param endpoint       the endpoint name
     * @param windowDuration the sliding window to consider
     * @return the number of attempts in the current window
     */
    public int getCurrentAttemptCount(String userId, String endpoint, Duration windowDuration) {
        String cacheKey = buildCacheKey(userId, endpoint);
        Instant windowStart = Instant.now().minus(windowDuration);

        CopyOnWriteArrayList<Instant> attempts = attemptsCache.getIfPresent(cacheKey);
        if (attempts == null) {
            return 0;
        }

        return (int) attempts.stream()
                .filter(ts -> !ts.isBefore(windowStart))
                .count();
    }

    // =========================================================================
    // Exception type
    // =========================================================================

    /**
     * Thrown when a rate limit has been exceeded.
     */
    public static class RateLimitExceededException extends RuntimeException {

        /**
         * @param message a descriptive message including the user, endpoint,
         *                and limit details
         */
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    // =========================================================================
    // Repository interface
    // =========================================================================

    /**
     * Repository contract for persisting rate-limit violation records.
     * Implementations may use JPA, JDBC, or any other persistence mechanism.
     */
    public interface RateLimitViolationRepository {

        /**
         * Records a rate-limit violation.
         *
         * @param userId         the offending user
         * @param endpoint       the endpoint that was rate-limited
         * @param attemptCount   the number of attempts made
         * @param maxAttempts    the configured limit
         * @param windowDuration the configured window
         * @param violationTime  the instant the violation occurred
         */
        void recordViolation(String userId, String endpoint,
                             int attemptCount, int maxAttempts,
                             Duration windowDuration, Instant violationTime);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private String buildCacheKey(String userId, String endpoint) {
        return userId + "::" + endpoint;
    }

    private void recordViolation(String userId, String endpoint,
                                 int attemptCount, int maxAttempts,
                                 Duration windowDuration) {
        try {
            violationRepository.recordViolation(
                    userId, endpoint,
                    attemptCount, maxAttempts,
                    windowDuration, Instant.now());
        } catch (Exception e) {
            // Never let persistence failures prevent the rate-limit from being enforced
            log.error("Failed to persist rate-limit violation for userId='{}', endpoint='{}'",
                    userId, endpoint, e);
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            return (seconds / 3600) + "h";
        }
    }
}
