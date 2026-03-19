package com.tuempresa.storage.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @JsonAlias({"name"})
        @Size(max = 160, message = "El nombre excede el maximo permitido.")
        String name,
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres.")
        String firstName,
        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres.")
        String lastName,
        @Email(message = "El correo no tiene un formato valido.")
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email,
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 8, max = 80, message = "La contrasena debe tener entre 8 y 80 caracteres.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,80}$",
                message = "La contrasena debe incluir mayuscula, minuscula, numero y simbolo."
        )
        String password,
        @Size(min = 8, max = 80, message = "La confirmacion debe tener entre 8 y 80 caracteres.")
        String confirmPassword,
        @NotBlank(message = "La nacionalidad es obligatoria.")
        @Size(max = 80, message = "La nacionalidad excede el maximo permitido.")
        String nationality,
        @NotBlank(message = "El idioma preferido es obligatorio.")
        @Pattern(
                regexp = "^(?i)(es|en|de|fr|it|pt)$",
                message = "Idioma no soportado."
        )
        String preferredLanguage,
        @NotBlank(message = "El telefono es obligatorio.")
        @Pattern(
                regexp = "^\\+[1-9]\\d{6,14}$",
                message = "El telefono debe estar en formato internacional E.164."
        )
        String phone,
        @NotNull(message = "Debes aceptar terminos y condiciones.")
        Boolean termsAccepted,
        @Size(max = 260, message = "La ruta de foto excede el maximo permitido.")
        String profilePhotoPath
) {
}
