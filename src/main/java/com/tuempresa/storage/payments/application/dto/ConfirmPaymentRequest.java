package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmPaymentRequest(
        Long paymentIntentId,
        @JsonAlias({"reservation_id"}) Long reservationId,
        Boolean approved,
        @Size(max = 120) String providerReference,
        @Size(max = 40) @JsonAlias({"method", "payment_method", "channel"}) String paymentMethod,
        @Size(max = 120) @JsonAlias({"token", "tokenId", "source_id", "sourceId"}) String sourceTokenId,
        @Size(max = 160) @JsonAlias({"email", "customer_email"}) String customerEmail,
        @Size(max = 80) @JsonAlias({"first_name", "firstName"}) String customerFirstName,
        @Size(max = 80) @JsonAlias({"last_name", "lastName"}) String customerLastName,
        @Size(max = 20) @JsonAlias({"phone", "phone_number", "phoneNumber"}) String customerPhone,
        @Size(max = 20) @JsonAlias({"document", "document_number", "dni"}) String customerDocument,
        @JsonAlias({"amount"}) BigDecimal amount,
        @Size(max = 80) @JsonAlias({"idempotency_key", "idempotencyKey"}) String idempotencyKey
) {
}
