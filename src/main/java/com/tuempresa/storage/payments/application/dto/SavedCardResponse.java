package com.tuempresa.storage.payments.application.dto;

import java.time.Instant;

public record SavedCardResponse(
    Long id,
    String alias,
    String brand,
    String lastFourDigits,
    String expirationMonth,
    String expirationYear,
    Instant createdAt,
    Instant lastUsedAt
) {}
