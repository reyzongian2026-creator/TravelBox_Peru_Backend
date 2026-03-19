package com.tuempresa.storage.reservations.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelReservationRequest(
        @NotBlank @Size(max = 240) String reason
) {
}
