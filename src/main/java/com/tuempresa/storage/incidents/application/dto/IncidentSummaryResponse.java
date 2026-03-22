package com.tuempresa.storage.incidents.application.dto;

import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.time.Instant;

public record IncidentSummaryResponse(
        Long id,
        Long reservationId,
        String reservationCode,
        ReservationStatus reservationStatus,
        String warehouseName,
        String warehouseAddress,
        Long openedBy,
        String openedByName,
        String openedByEmail,
        Long customerId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerWhatsappUrl,
        String customerCallUrl,
        IncidentStatus status,
        String description,
        String resolution,
        String originalLanguage,
        Instant createdAt,
        Instant resolvedAt
) {
}
