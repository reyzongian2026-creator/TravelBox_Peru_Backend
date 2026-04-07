package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuempresa.storage.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentIntentResponse(
        Long id,
        Long reservationId,
        BigDecimal amount,
        PaymentStatus status,
        String providerReference,
        Instant createdAt,
        String paymentProvider,
        String paymentMethod,
        String paymentFlow,
        String message,
        Map<String, Object> nextAction,

        // QR signing (fraud prevention)
        String qrSignature,
        Long qrSignatureTimestamp,

        // Identity verification
        String payerName,
        String payerPhone,
        LocalDateTime verificationDate,

        // Verification status (for UI badge)
        String verificationStatus
) {
    public PaymentIntentResponse(
            Long id,
            Long reservationId,
            BigDecimal amount,
            PaymentStatus status,
            String providerReference,
            Instant createdAt
    ) {
        this(id, reservationId, amount, status, providerReference, createdAt,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * Backwards-compatible constructor without fraud prevention / verification fields.
     */
    public PaymentIntentResponse(
            Long id,
            Long reservationId,
            BigDecimal amount,
            PaymentStatus status,
            String providerReference,
            Instant createdAt,
            String paymentProvider,
            String paymentMethod,
            String paymentFlow,
            String message,
            Map<String, Object> nextAction
    ) {
        this(id, reservationId, amount, status, providerReference, createdAt,
                paymentProvider, paymentMethod, paymentFlow, message, nextAction,
                null, null, null, null, null, null);
    }

    @JsonProperty("paymentIntentId")
    public Long paymentIntentId() {
        return id;
    }

    @JsonProperty("intentId")
    public Long intentId() {
        return id;
    }

    @JsonProperty("paymentId")
    public Long paymentId() {
        return id;
    }

    @JsonProperty("paymentReference")
    public String paymentReference() {
        return providerReference;
    }

    @JsonProperty("orderId")
    public String orderId() {
        if (nextAction == null) {
            return null;
        }
        Object value = nextAction.get("orderId");
        return value != null ? String.valueOf(value) : null;
    }
}
