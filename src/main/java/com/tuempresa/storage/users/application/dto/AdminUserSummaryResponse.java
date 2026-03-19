package com.tuempresa.storage.users.application.dto;

public record AdminUserSummaryResponse(
        long totalUsers,
        long activeUsers,
        long operatorUsers,
        long courierUsers,
        long completedDeliveries
) {
}
