package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentIntentRequest(@NotNull @JsonAlias({"reservation_id"}) Long reservationId) {
}
