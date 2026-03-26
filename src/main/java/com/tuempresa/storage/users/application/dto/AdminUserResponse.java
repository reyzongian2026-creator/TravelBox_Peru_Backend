package com.tuempresa.storage.users.application.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String nationality,
        String preferredLanguage,
        String authProvider,
        boolean managedByAdmin,
        String documentType,
        String documentNumber,
        String profilePhotoPath,
        String documentPhotoPath,
        String vehiclePlate,
        boolean emailVerified,
        boolean profileCompleted,
        boolean active,
        List<String> roles,
        List<Long> warehouseIds,
        List<String> warehouseNames,
        long deliveryCreatedCount,
        long deliveryAssignedCount,
        long deliveryCompletedCount,
        long activeDeliveryCount,
        Instant createdAt
) {
}
