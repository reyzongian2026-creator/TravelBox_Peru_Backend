package com.tuempresa.storage.sensitivedata.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SensitiveDataDecryptRequest(
        @NotBlank(message = "La contrasena es requerida.")
        String password
) {
}
