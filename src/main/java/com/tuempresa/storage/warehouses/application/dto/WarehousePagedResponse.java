package com.tuempresa.storage.warehouses.application.dto;

import java.time.Instant;

public record WarehousePagedResponse(
        Long id,
        String name,
        String cityName,
        boolean active,
        String imageUrl,
        int capacity,
        Instant createdAt
) {
}
