package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank(message = "El nuevo correo es requerido.")
        @Email(message = "El correo no tiene un formato valido.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String newEmail,
        
        @NotBlank(message = "La contrasena es requerida.")
        @Size(min = 8, max = 80, message = "La contrasena debe tener entre 8 y 80 caracteres.")
        String currentPassword
) {
}
