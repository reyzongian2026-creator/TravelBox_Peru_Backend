package com.tuempresa.storage.users.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserPasswordRequest(
        @NotBlank(message = "Debes ingresar una nueva contrasena.")
        @Size(min = 8, max = 120, message = "La contrasena debe tener entre 8 y 120 caracteres.")
        String password
) {
}
