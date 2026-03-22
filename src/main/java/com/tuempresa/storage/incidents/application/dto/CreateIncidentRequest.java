package com.tuempresa.storage.incidents.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
        @NotNull Long reservationId,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 5) String originalLanguage
) {
}
