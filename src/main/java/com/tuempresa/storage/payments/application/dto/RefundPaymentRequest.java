package com.tuempresa.storage.payments.application.dto;

import jakarta.validation.constraints.Size;

public record RefundPaymentRequest(
        @Size(max = 500) String reason
) {
}

