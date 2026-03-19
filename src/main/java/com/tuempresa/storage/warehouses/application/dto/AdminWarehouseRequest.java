package com.tuempresa.storage.warehouses.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminWarehouseRequest(
        @NotNull Long cityId,
        Long zoneId,
        @NotBlank @Size(max = 140) String name,
        @NotBlank @Size(max = 220) String address,
        @Min(-90) @Max(90) double latitude,
        @Min(-180) @Max(180) double longitude,
        @Min(1) int capacity,
        @NotBlank @Size(max = 10) String openHour,
        @NotBlank @Size(max = 10) String closeHour,
        @Size(max = 600) String rules,
        Boolean active,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerHourSmall,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerHourMedium,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerHourLarge,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerHourExtraLarge,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal pickupFee,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal dropoffFee,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2) BigDecimal insuranceFee
) {
}
