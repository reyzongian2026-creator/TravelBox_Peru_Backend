package com.tuempresa.storage.profile.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres.")
        String firstName,
        @Size(min = 2, max = 80, message = "El apellido debe tener entre 2 y 80 caracteres.")
        String lastName,
        @Email(message = "El correo no tiene un formato valido.")
        @Size(max = 160, message = "El correo excede el maximo permitido.")
        String email,
        @Pattern(
                regexp = "^\\+[1-9]\\d{6,14}$",
                message = "El telefono debe estar en formato internacional E.164."
        )
        String phone,
        @Size(max = 80, message = "La nacionalidad excede el maximo permitido.")
        String nationality,
        @Pattern(
                regexp = "^(?i)(es|en|de|fr|it|pt)$",
                message = "Idioma no soportado."
        )
        String preferredLanguage,
        LocalDate birthDate,
        @Pattern(
                regexp = "^(?i)(FEMALE|MALE|NON_BINARY|PREFER_NOT_TO_SAY|OTHER|MUJER|HOMBRE|NO_BINARIO|PREFIERO_NO_DECIRLO|OTRO)$",
                message = "Genero no soportado."
        )
        String gender,
        @Size(max = 260, message = "La ruta de foto excede el maximo permitido.")
        String profilePhotoPath,
        @Size(max = 220, message = "La direccion excede el maximo permitido.")
        String address,
        @Size(max = 120, message = "La ciudad excede el maximo permitido.")
        String city,
        @Size(max = 120, message = "El pais excede el maximo permitido.")
        String country,
        @Pattern(
                regexp = "^(?i)(DNI|PASSPORT|PASAPORTE|FOREIGNER_CARD|CARNE_DE_EXTRANJERIA|CARNET_DE_EXTRANJERIA|ID_CARD|CEDULA|CEDULA_DE_IDENTIDAD|DRIVER_LICENSE|LICENCIA_DE_CONDUCIR|LICENCE|OTHER|OTRO)$",
                message = "Tipo de documento no soportado."
        )
        String documentType,
        @Pattern(
                regexp = "^[A-Za-z0-9\\-]{5,60}$",
                message = "El documento debe tener entre 5 y 60 caracteres alfanumericos."
        )
        String documentNumber,
        @Pattern(
                regexp = "^(?i)(DNI|PASSPORT|PASAPORTE|FOREIGNER_CARD|CARNE_DE_EXTRANJERIA|CARNET_DE_EXTRANJERIA|ID_CARD|CEDULA|CEDULA_DE_IDENTIDAD|DRIVER_LICENSE|LICENCIA_DE_CONDUCIR|LICENCE|OTHER|OTRO)$",
                message = "Tipo de documento secundario no soportado."
        )
        String secondaryDocumentType,
        @Pattern(
                regexp = "^[A-Za-z0-9\\-]{5,60}$",
                message = "El documento secundario debe tener entre 5 y 60 caracteres alfanumericos."
        )
        String secondaryDocumentNumber,
        @Size(max = 120, message = "El nombre de contacto excede el maximo permitido.")
        String emergencyContactName,
        @Pattern(
                regexp = "^\\+[1-9]\\d{6,14}$",
                message = "El telefono de emergencia debe estar en formato internacional E.164."
        )
        String emergencyContactPhone,
        @Size(min = 8, max = 80, message = "La contrasena actual debe tener entre 8 y 80 caracteres.")
        String currentPassword
) {
}
