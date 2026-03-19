package com.tuempresa.storage.payments.application.dto;

import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentStatusResponse(
        Long paymentIntentId,
        Long reservationId,
        PaymentStatus paymentStatus,
        ReservationStatus reservationStatus,
        BigDecimal amount,
        String paymentMethod,
        String paymentProvider,
        String providerReference,
        String paymentFlow,
        String gatewayStatus,
        String gatewayMessage,
        Instant paymentCreatedAt,
        Instant reservationExpiresAt
) {
}
