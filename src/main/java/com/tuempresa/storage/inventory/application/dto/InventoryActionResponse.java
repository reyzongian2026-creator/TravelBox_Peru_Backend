package com.tuempresa.storage.inventory.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.time.Instant;

public record InventoryActionResponse(
        Long reservationId,
        ReservationStatus reservationStatus,
        String action,
        Long operatorId,
        Instant processedAt,
        String evidenceUrl
) {
    @JsonProperty("imageUrl")
    public String imageUrl() {
        return evidenceUrl;
    }

    @JsonProperty("photoUrl")
    public String photoUrl() {
        return evidenceUrl;
    }
}
