package com.tuempresa.storage.incidents.application.dto;

import com.tuempresa.storage.incidents.domain.IncidentStatus;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long reservationId,
        IncidentStatus status,
        String description,
        String resolution,
        Long openedBy,
        Long resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
}
