package com.tuempresa.storage.delivery.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeliveryOrderRequest(
        @NotNull Long reservationId,
        @NotBlank @Size(max = 30) String type,
        @NotBlank @Size(max = 220) String address,
        @Size(max = 120) String zone,
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") Double latitude,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") Double longitude
) {
}
