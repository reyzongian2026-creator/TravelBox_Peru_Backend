package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Repository
public class JdbcRateLimitViolationRepository implements RateLimiter.RateLimitViolationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRateLimitViolationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public JdbcRateLimitViolationRepository(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public void recordViolation(
            String userId,
            String endpoint,
            int attemptCount,
            int maxAttempts,
            Duration windowDuration,
            Instant violationTime
    ) {
        Long resolvedUserId = resolveUserId(userId);
        if (resolvedUserId == null) {
            log.warn(
                    "Skipping rate-limit violation persistence because userId '{}' could not be resolved (endpoint={}, attempts={}, max={}, window={})",
                    userId,
                    endpoint,
                    attemptCount,
                    maxAttempts,
                    windowDuration
            );
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO rate_limit_violations (
                    user_id,
                    endpoint,
                    attempt_count,
                    limit_threshold,
                    source_ip,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                resolvedUserId,
                endpoint,
                attemptCount,
                maxAttempts,
                null,
                Timestamp.from(violationTime)
        );
    }

    private Long resolveUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ignored) {
            return userRepository.findByEmailIgnoreCase(userId.trim())
                    .map(User::getId)
                    .orElse(null);
        }
    }
}
