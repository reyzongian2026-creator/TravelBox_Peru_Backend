package com.tuempresa.storage.users.application.dto;

import com.tuempresa.storage.users.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateAdminUserRequest(
        @NotBlank(message = "Debes ingresar nombre completo.")
        @Size(max = 160, message = "El nombre completo no puede exceder 160 caracteres.")
        String fullName,
        @Email(message = "Debes ingresar un correo valido.")
        @NotBlank(message = "Debes ingresar correo.")
        @Size(max = 160, message = "El correo no puede exceder 160 caracteres.")
        String email,
        @NotBlank(message = "Debes ingresar telefono.")
        @Size(max = 30, message = "El telefono no puede exceder 30 caracteres.")
        String phone,
        @NotBlank(message = "Debes definir una contrasena temporal.")
        @Size(min = 8, max = 120, message = "La contrasena debe tener entre 8 y 120 caracteres.")
        String password,
        @NotEmpty(message = "Debes asignar al menos un rol.")
        Set<Role> roles,
        @Size(max = 80, message = "La nacionalidad no puede exceder 80 caracteres.")
        String nationality,
        @Size(max = 10, message = "El idioma preferido no puede exceder 10 caracteres.")
        String preferredLanguage,
        @Size(max = 40, message = "El tipo de documento no puede exceder 40 caracteres.")
        String documentType,
        @Size(max = 60, message = "El numero de documento no puede exceder 60 caracteres.")
        String documentNumber,
        @Size(max = 260, message = "La URL de la foto del documento no puede exceder 260 caracteres.")
        String documentPhotoPath,
        @Size(max = 30, message = "La placa no puede exceder 30 caracteres.")
        String vehiclePlate,
        Boolean active,
        Set<Long> warehouseIds
) {
}
