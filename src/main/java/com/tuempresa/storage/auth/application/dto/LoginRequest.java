package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email(message = "El correo no tiene un formato valido.")
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email,
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 8, max = 80, message = "La contrasena debe tener entre 8 y 80 caracteres.")
        String password
) {
}
