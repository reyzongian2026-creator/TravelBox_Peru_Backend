package com.tuempresa.storage.delivery.application.dto;

import jakarta.validation.constraints.Size;

public record CourierClaimDeliveryRequest(
        @Size(max = 40) String vehicleType,
        @Size(max = 30) String vehiclePlate
) {
}
