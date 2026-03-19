package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FirebaseSocialAuthRequest(
        @NotBlank(message = "Debes enviar el idToken de Firebase.")
        String idToken,
        @NotBlank(message = "Debes indicar el proveedor.")
        @Pattern(regexp = "^(?i)(GOOGLE|FACEBOOK)$", message = "Proveedor no soportado.")
        String provider,
        @NotNull(message = "Debes confirmar la aceptacion de terminos.")
        Boolean termsAccepted,
        @Size(max = 160, message = "El nombre excede el maximo permitido.")
        String displayName,
        @Size(max = 260, message = "La foto excede el maximo permitido.")
        String profilePhotoUrl
) {
}
