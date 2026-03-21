package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.reservations.domain.Rating;

import java.time.Instant;

public record AdminRatingResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        Long warehouseId,
        String warehouseName,
        Long reservationId,
        int stars,
        String comment,
        String type,
        boolean verified,
        Instant createdAt
) {
    public static AdminRatingResponse from(Rating rating) {
        return new AdminRatingResponse(
                rating.getId(),
                rating.getUser().getId(),
                rating.getUser().getFullName(),
                rating.getUser().getEmail(),
                rating.getWarehouse().getId(),
                rating.getWarehouse().getName(),
                rating.getReservation() != null ? rating.getReservation().getId() : null,
                rating.getStars(),
                rating.getComment(),
                rating.getType().name(),
                rating.isVerified(),
                rating.getCreatedAt()
        );
    }
}
