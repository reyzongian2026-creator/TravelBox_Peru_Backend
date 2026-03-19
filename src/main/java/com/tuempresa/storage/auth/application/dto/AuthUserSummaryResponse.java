package com.tuempresa.storage.auth.application.dto;

import java.util.List;

public record AuthUserSummaryResponse(
        Long id,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String phone,
        String nationality,
        String preferredLanguage,
        String authProvider,
        boolean managedByAdmin,
        boolean canSelfEditProfile,
        String vehiclePlate,
        String profilePhotoPath,
        boolean emailVerified,
        boolean profileCompleted,
        int emailChangeRemaining,
        int phoneChangeRemaining,
        int documentChangeRemaining,
        List<String> roles,
        List<Long> warehouseIds,
        List<String> warehouseNames
) {
}
