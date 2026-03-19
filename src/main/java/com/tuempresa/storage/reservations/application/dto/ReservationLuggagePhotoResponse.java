package com.tuempresa.storage.reservations.application.dto;

import java.time.Instant;

public record ReservationLuggagePhotoResponse(
        Long id,
        String type,
        Integer bagUnitIndex,
        String imageUrl,
        Instant capturedAt,
        Long capturedByUserId,
        String capturedByName
) {
}
