package com.tuempresa.storage.reservations.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAssistedReservationRequest(
        @NotNull Long warehouseId,
        @NotNull @Future Instant startAt,
        @NotNull @Future Instant endAt,
        @Min(1) int estimatedItems,
        @Size(max = 20) String bagSize,
        Boolean pickupRequested,
        Boolean dropoffRequested,
        Boolean deliveryRequested,
        Boolean extraInsurance,
        @NotBlank(message = "Debes ingresar nombre del cliente.")
        @Size(max = 160, message = "El nombre del cliente no puede exceder 160 caracteres.")
        String customerFullName,
        @NotBlank(message = "Debes ingresar correo del cliente.")
        @Email(message = "Debes ingresar un correo valido para el cliente.")
        @Size(max = 160, message = "El correo del cliente no puede exceder 160 caracteres.")
        String customerEmail,
        @NotBlank(message = "Debes ingresar telefono del cliente.")
        @Size(max = 30, message = "El telefono del cliente no puede exceder 30 caracteres.")
        String customerPhone,
        @Size(max = 80, message = "La nacionalidad no puede exceder 80 caracteres.")
        String customerNationality,
        @Size(max = 10, message = "El idioma preferido no puede exceder 10 caracteres.")
        String customerPreferredLanguage
) {
}
