package com.tuempresa.storage.users.application.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserPagedResponse(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active,
        List<Long> warehouseIds,
        Instant createdAt
) {
}
