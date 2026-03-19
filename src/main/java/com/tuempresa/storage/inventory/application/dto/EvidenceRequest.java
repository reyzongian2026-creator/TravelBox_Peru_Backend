package com.tuempresa.storage.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvidenceRequest(
        @NotNull Long reservationId,
        @NotBlank @Size(max = 30) String type,
        @NotBlank @Size(max = 300) String url,
        @Size(max = 240) String observation
) {
}
