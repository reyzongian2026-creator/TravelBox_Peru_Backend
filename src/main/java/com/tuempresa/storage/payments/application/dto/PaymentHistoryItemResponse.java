package com.tuempresa.storage.payments.application.dto;

import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentHistoryItemResponse(
        Long paymentIntentId,
        Long reservationId,
        Long userId,
        String userEmail,
        BigDecimal amount,
        PaymentStatus paymentStatus,
        ReservationStatus reservationStatus,
        String paymentMethod,
        String paymentProvider,
        String paymentFlow,
        String providerReference,
        String gatewayStatus,
        String gatewayMessage,
        Instant createdAt,
        ReconciliationInfo reconciliation
) {
    public record ReconciliationInfo(
            String outcome,
            String matchReason,
            String matchedFields,
            String senderName,
            String senderEmail,
            String txDateTimeRaw,
            Instant receivedAt,
            String messageId
    ) {
    }
}
