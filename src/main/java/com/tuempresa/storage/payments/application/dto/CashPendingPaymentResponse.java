package com.tuempresa.storage.payments.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashPendingPaymentResponse(
                Long paymentIntentId,
                Long reservationId,
                Long userId,
                String userEmail,
                String userName,
                BigDecimal amount,
                String paymentMethod,
                String providerReference,
                String gatewayStatus,
                String gatewayMessage,
                Instant createdAt,
                Instant reservationStartAt,
                Instant reservationEndAt) {
}
