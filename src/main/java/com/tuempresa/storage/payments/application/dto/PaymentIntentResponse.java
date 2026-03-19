package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuempresa.storage.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
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
        Map<String, Object> nextAction
) {
    public PaymentIntentResponse(
            Long id,
            Long reservationId,
            BigDecimal amount,
            PaymentStatus status,
            String providerReference,
            Instant createdAt
    ) {
        this(id, reservationId, amount, status, providerReference, createdAt, null, null, null, null, null);
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
