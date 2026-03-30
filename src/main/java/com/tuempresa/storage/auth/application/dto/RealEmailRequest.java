package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RealEmailRequest(
        @NotBlank(message = "El correo es requerido.")
        @Email(message = "El correo no tiene un formato valido.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email
) {
}
