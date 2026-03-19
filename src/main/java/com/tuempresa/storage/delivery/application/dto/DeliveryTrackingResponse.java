package com.tuempresa.storage.delivery.application.dto;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;

import java.time.Instant;
import java.util.List;

public record DeliveryTrackingResponse(
        Long deliveryOrderId,
        Long reservationId,
        DeliveryStatus status,
        String driverName,
        String driverPhone,
        String vehicleType,
        String vehiclePlate,
        double currentLatitude,
        double currentLongitude,
        double destinationLatitude,
        double destinationLongitude,
        Integer etaMinutes,
        String trackingMode,
        boolean reconnectSuggested,
        Instant lastUpdatedAt,
        List<DeliveryTrackingEventResponse> events
) {
}
