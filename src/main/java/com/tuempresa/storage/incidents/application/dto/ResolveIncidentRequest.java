package com.tuempresa.storage.incidents.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
        @NotBlank @Size(max = 500) String resolution
) {
}
