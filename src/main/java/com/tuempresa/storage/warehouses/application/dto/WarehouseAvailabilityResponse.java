package com.tuempresa.storage.warehouses.application.dto;

import java.time.Instant;

public record WarehouseAvailabilityResponse(
        Long warehouseId,
        String warehouseName,
        String address,
        Long cityId,
        String cityName,
        Instant startAt,
        Instant endAt,
        int totalCapacity,
        long reservedInRange,
        int availableInRange,
        boolean hasAvailability
) {
}
