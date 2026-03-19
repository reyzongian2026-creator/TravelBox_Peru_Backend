package com.tuempresa.storage.delivery.application.dto;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;

import java.time.Instant;

public record DeliveryTrackingEventResponse(
        int sequence,
        DeliveryStatus status,
        double latitude,
        double longitude,
        Integer etaMinutes,
        String message,
        Instant createdAt
) {
}
