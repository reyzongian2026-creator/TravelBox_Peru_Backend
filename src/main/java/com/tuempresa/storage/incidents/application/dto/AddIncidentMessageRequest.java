package com.tuempresa.storage.incidents.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddIncidentMessageRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 5) String originalLanguage
) {
}
