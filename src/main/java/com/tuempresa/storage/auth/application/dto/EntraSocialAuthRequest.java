package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EntraSocialAuthRequest(
        @NotBlank(message = "Debes enviar el accessToken de Microsoft Entra.")
        String accessToken,
        @NotBlank(message = "Debes indicar el proveedor.")
        @Pattern(regexp = "^(?i)(MICROSOFT|ENTRA|AZURE)$", message = "Proveedor no soportado.")
        String provider,
        @NotNull(message = "Debes confirmar la aceptacion de terminos.")
        Boolean termsAccepted,
        @Size(max = 160, message = "El nombre excede el maximo permitido.")
        String displayName,
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email
) {
}
