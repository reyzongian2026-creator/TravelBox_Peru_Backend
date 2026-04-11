package com.tuempresa.storage.incidents.application.dto;

import java.time.Instant;

public record IncidentMessageResponse(
        Long id,
        Long incidentId,
        Long authorId,
        String authorName,
        String authorRole,
        String originalLanguage,
        String textOriginal,
        String textTranslated,
        String imageUrl,
        Instant createdAt
) {
}
