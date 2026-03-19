package com.tuempresa.storage.reservations.application.dto;

import java.time.Instant;
import java.util.List;

public record ReservationOperationalDetailResponse(
        String stage,
        String bagTagId,
        String bagTagQrPayload,
        Integer bagUnits,
        boolean pickupPinGenerated,
        boolean pickupPinVisible,
        String pickupPin,
        boolean canViewLuggagePhotos,
        boolean luggagePhotosLocked,
        Integer expectedLuggagePhotos,
        Integer storedLuggagePhotos,
        Instant checkinAt,
        Instant lastCheckoutAt,
        List<ReservationLuggagePhotoResponse> luggagePhotos
) {
}
