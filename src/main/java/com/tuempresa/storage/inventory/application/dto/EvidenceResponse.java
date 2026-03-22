package com.tuempresa.storage.inventory.application.dto;

import java.time.Instant;

public record EvidenceResponse(
        Long id,
        String type,
        String url,
        String description,
        Instant uploadedAt,
        String uploadedBy
) {
}
