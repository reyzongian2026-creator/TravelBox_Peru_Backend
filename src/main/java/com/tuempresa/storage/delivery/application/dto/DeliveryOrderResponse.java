package com.tuempresa.storage.delivery.application.dto;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DeliveryOrderResponse(
        Long id,
        Long reservationId,
        String type,
        String address,
        String zone,
        DeliveryStatus status,
        BigDecimal cost,
        Long assignedCourierId,
        String driverName,
        String driverPhone,
        String vehicleType,
        String vehiclePlate,
        Integer etaMinutes,
        Instant createdAt
) {
}
