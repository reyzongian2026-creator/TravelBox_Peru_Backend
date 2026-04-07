package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcRateLimitViolationRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserRepository userRepository;

    private JdbcRateLimitViolationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcRateLimitViolationRepository(jdbcTemplate, userRepository);
    }

    @Test
    void shouldPersistViolationWhenUserIdIsNumeric() {
        repository.recordViolation("25", "/payments/confirm", 3, 2, Duration.ofMinutes(1), Instant.now());

        verify(jdbcTemplate).update(any(String.class), eq(25L), eq("/payments/confirm"), eq(3), eq(2), eq(null), any());
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void shouldResolveUserIdFromEmailBeforePersisting() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(44L);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        repository.recordViolation("user@example.com", "/payments/intents", 5, 4, Duration.ofMinutes(1), Instant.now());

        verify(userRepository).findByEmailIgnoreCase("user@example.com");
        verify(jdbcTemplate).update(any(String.class), eq(44L), eq("/payments/intents"), eq(5), eq(4), eq(null), any());
    }

    @Test
    void shouldSkipInsertWhenUserCannotBeResolved() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        repository.recordViolation("missing@example.com", "/payments/intents", 5, 4, Duration.ofMinutes(1), Instant.now());

        verify(userRepository).findByEmailIgnoreCase("missing@example.com");
        verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any(), any(), any(), any());
    }
}
