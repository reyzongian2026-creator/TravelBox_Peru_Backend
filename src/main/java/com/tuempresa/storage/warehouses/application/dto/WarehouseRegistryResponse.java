package com.tuempresa.storage.warehouses.application.dto;

import java.time.Instant;

public record WarehouseRegistryResponse(
        Long id,
        String name,
        String address,
        Long cityId,
        String cityName,
        double latitude,
        double longitude,
        int capacity,
        int occupied,
        int available,
        boolean active,
        String openHour,
        String closeHour,
        Instant createdAt,
        Instant updatedAt
) {
}

