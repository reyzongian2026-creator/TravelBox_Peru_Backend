package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentWebhookResponse(
        boolean processed,
        boolean idempotent,
        String eventId,
        String eventType,
        String providerReference,
        String providerStatus,
        String message,
        Long paymentIntentId,
        Long reservationId
) {
}
