package com.tuempresa.storage.reservations.application.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReservationRequest(
        @NotNull Long warehouseId,
        @NotNull @Future Instant startAt,
        @NotNull @Future Instant endAt,
        @Min(1) int estimatedItems,
        @Size(max = 20) String bagSize,
        Boolean pickupRequested,
        Boolean dropoffRequested,
        Boolean deliveryRequested,
        Boolean extraInsurance
) {
}
