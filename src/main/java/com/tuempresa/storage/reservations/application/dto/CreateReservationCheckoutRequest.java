package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReservationCheckoutRequest(
        @NotNull Long warehouseId,
        @NotNull @Future Instant startAt,
        @NotNull @Future Instant endAt,
        @Min(1) int estimatedItems,
        @Size(max = 20) String bagSize,
        Boolean pickupRequested,
        Boolean dropoffRequested,
        Boolean deliveryRequested,
        Boolean extraInsurance,
        @Size(max = 40) String paymentMethod,
        @Size(max = 120) String sourceTokenId,
        @Size(max = 160) String customerEmail,
        @Size(max = 80) String customerFirstName,
        @Size(max = 80) String customerLastName,
        @Size(max = 20) String customerPhone,
        @Size(max = 20) String customerDocument,
        @Size(max = 120) String providerReference
) {
    public CreateReservationRequest toReservationRequest() {
        return new CreateReservationRequest(
                warehouseId,
                startAt,
                endAt,
                estimatedItems,
                bagSize,
                pickupRequested,
                dropoffRequested,
                deliveryRequested,
                extraInsurance
        );
    }

    public ConfirmPaymentRequest toPaymentRequest(Long reservationId) {
        return new ConfirmPaymentRequest(
                null,
                reservationId,
                true,
                providerReference,
                paymentMethod,
                sourceTokenId,
                customerEmail,
                customerFirstName,
                customerLastName,
                customerPhone,
                customerDocument
        );
    }
}
