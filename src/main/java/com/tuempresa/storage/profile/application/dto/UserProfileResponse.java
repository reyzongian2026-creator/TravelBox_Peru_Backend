package com.tuempresa.storage.profile.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String fullName,
        String firstName,
        String lastName,
        String email,
        String pendingRealEmail,
        String phone,
        String nationality,
        String preferredLanguage,
        String authProvider,
        boolean managedByAdmin,
        boolean canSelfEditProfile,
        String vehiclePlate,
        boolean emailVerified,
        boolean requiresRealEmailCompletion,
        boolean profileCompleted,
        LocalDate birthDate,
        String gender,
        String profilePhotoPath,
        String address,
        String city,
        String country,
        String documentType,
        String documentNumber,
        String secondaryDocumentType,
        String secondaryDocumentNumber,
        String emergencyContactName,
        String emergencyContactPhone,
        Instant termsAcceptedAt,
        Instant emailVerificationExpiresAt,
        String verificationCodePreview,
        int emailChangeRemaining,
        int phoneChangeRemaining,
        int documentChangeRemaining,
        List<String> roles,
        List<Long> warehouseIds,
        List<String> warehouseNames
) {
}
