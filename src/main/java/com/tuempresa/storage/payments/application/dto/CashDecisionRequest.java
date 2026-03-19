package com.tuempresa.storage.payments.application.dto;

import jakarta.validation.constraints.Size;

public record CashDecisionRequest(
        @Size(max = 120) String providerReference,
        @Size(max = 240) String reason
) {
}
