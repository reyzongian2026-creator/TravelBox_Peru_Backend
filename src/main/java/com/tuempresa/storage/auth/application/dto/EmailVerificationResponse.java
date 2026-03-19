package com.tuempresa.storage.auth.application.dto;

import java.time.Instant;

public record EmailVerificationResponse(
        boolean emailVerified,
        String message,
        String verificationCodePreview,
        Instant expiresAt
) {
}
