package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpsApprovalRequest(
        @NotBlank(message = "El mensaje para operador es obligatorio.")
        @Size(max = 260, message = "El mensaje para operador excede el maximo permitido.")
        String messageForOperator,
        @NotBlank(message = "El mensaje para cliente es obligatorio.")
        @Size(max = 320, message = "El mensaje para cliente excede el maximo permitido.")
        String messageForCustomerSpanish,
        @Pattern(
                regexp = "^(?i)(es|en|de|fr|it|pt)$",
                message = "Idioma no soportado."
        )
        String customerLanguage
) {
}
