package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record ValidateCheckoutResultRequest(
        @NotBlank @JsonAlias({"kr-answer", "krAnswer", "clientAnswer"}) String krAnswer,
        @NotBlank @JsonAlias({"kr-hash", "krHash", "hash"}) String krHash,
        @JsonAlias({"paymentIntentId", "payment_intent_id"}) Long paymentIntentId,
        @JsonAlias({"reservationId", "reservation_id"}) Long reservationId
) {
}
