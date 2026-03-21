package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.reservations.domain.ReservationStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record BulkReservationStatusRequest(
        @NotEmpty Set<Long> ids,
        @NotNull ReservationStatus status
) {
}
