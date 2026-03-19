package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpsPinRequest(
        @NotBlank(message = "El PIN es obligatorio.")
        @Pattern(regexp = "^\\d{4,8}$", message = "El PIN debe contener solo numeros (4 a 8 digitos).")
        String pin,
        @Size(max = 400, message = "Las notas exceden el maximo permitido.")
        String notes
) {
}
