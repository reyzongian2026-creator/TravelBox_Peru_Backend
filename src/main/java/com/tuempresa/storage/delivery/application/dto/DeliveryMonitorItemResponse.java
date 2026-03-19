package com.tuempresa.storage.delivery.application.dto;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.time.Instant;

public record DeliveryMonitorItemResponse(
        Long deliveryOrderId,
        Long reservationId,
        String reservationCode,
        String deliveryType,
        ReservationStatus reservationStatus,
        DeliveryStatus deliveryStatus,
        String warehouseName,
        String cityName,
        String customerName,
        String customerEmail,
        String address,
        String zone,
        Long assignedCourierId,
        String driverName,
        String driverPhone,
        String vehicleType,
        String vehiclePlate,
        Double currentLatitude,
        Double currentLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        Integer etaMinutes,
        Instant updatedAt
) {
}
