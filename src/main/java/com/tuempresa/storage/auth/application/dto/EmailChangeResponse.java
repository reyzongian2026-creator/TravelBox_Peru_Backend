package com.tuempresa.storage.auth.application.dto;

import java.time.Instant;

public record EmailChangeResponse(
        Long userId,
        String newEmail,
        String maskedNewEmail,
        Instant expiresAt,
        int remainingAttempts
) {
}
