package com.tuempresa.storage.inventory.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckinRequest(
        @NotNull Long reservationId,
        @Size(max = 400) String notes
) {
}
