package com.tuempresa.storage.payments.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentIntentRequest(
        @NotNull @JsonAlias({
                "reservation_id" }) Long reservationId,
        @JsonAlias({ "promo_code" }) String promoCode,
        @JsonAlias({ "wallet_amount" }) BigDecimal walletAmount) {
}
