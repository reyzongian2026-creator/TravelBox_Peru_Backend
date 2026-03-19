package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @Email(message = "El correo no tiene un formato valido.")
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email,
        @NotBlank(message = "El codigo de recuperacion es obligatorio.")
        @Size(min = 4, max = 20, message = "El codigo de recuperacion no es valido.")
        String code,
        @NotBlank(message = "La nueva contrasena es obligatoria.")
        @Size(min = 8, max = 80, message = "La contrasena debe tener entre 8 y 80 caracteres.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,80}$",
                message = "La contrasena debe incluir mayuscula, minuscula, numero y simbolo."
        )
        String newPassword,
        @Size(min = 8, max = 80, message = "La confirmacion debe tener entre 8 y 80 caracteres.")
        String confirmPassword
) {
}
