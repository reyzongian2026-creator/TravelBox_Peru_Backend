package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpsQrScanRequest(
        @NotBlank(message = "El valor escaneado es obligatorio.")
        @Size(max = 240, message = "El valor escaneado excede el maximo permitido.")
        String scannedValue,
        @Pattern(
                regexp = "^(?i)(es|en|de|fr|it|pt)$",
                message = "Idioma no soportado."
        )
        String customerLanguage
) {
}
