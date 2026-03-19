package com.tuempresa.storage.auth.application.dto;

import java.time.Instant;
import java.util.Set;

public record AuthTokenResponse(
        Long userId,
        String email,
        Set<String> roles,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        AuthUserSummaryResponse user,
        boolean emailVerified,
        boolean profileCompleted,
        boolean requiresEmailVerification,
        String verificationCodePreview,
        String accountState
) {
}
