package com.tuempresa.storage.auth.application.dto;

import java.time.Instant;

public record PasswordResetResponse(
        String message,
        String resetCodePreview,
        Instant expiresAt
) {
}
