package com.tuempresa.storage.delivery.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourierTrackingUpdateRequest(
        @NotBlank @Size(max = 30) String status,
        @Min(-90) @Max(90) Double latitude,
        @Min(-180) @Max(180) Double longitude,
        @Min(0) Integer etaMinutes,
        @Size(max = 220) String message,
        @Size(max = 40) String vehicleType,
        @Size(max = 30) String vehiclePlate
) {
}
