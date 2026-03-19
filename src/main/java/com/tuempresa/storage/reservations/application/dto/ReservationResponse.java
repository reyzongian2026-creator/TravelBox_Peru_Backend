package com.tuempresa.storage.reservations.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long userId,
        Long warehouseId,
        String warehouseName,
        String warehouseAddress,
        String cityName,
        String zoneName,
        Double warehouseLatitude,
        Double warehouseLongitude,
        Instant startAt,
        Instant endAt,
        ReservationStatus status,
        BigDecimal totalPrice,
        Integer estimatedItems,
        String bagSize,
        boolean pickupRequested,
        boolean dropoffRequested,
        boolean extraInsurance,
        BigDecimal storageAmount,
        BigDecimal pickupFee,
        BigDecimal dropoffFee,
        BigDecimal insuranceFee,
        BigDecimal latePickupSurcharge,
        String qrCode,
        String qrImageUrl,
        String qrImageDataUrl,
        Instant expiresAt,
        String cancelReason,
        ReservationOperationalDetailResponse operationalDetail
) {
    @JsonProperty("bagCount")
    public int bagCount() {
        return estimatedItems != null && estimatedItems > 0 ? estimatedItems : 1;
    }

    @JsonProperty("deliveryRequested")
    public boolean deliveryRequested() {
        return dropoffRequested;
    }

    @JsonProperty("warehouseLat")
    public Double warehouseLat() {
        return warehouseLatitude;
    }

    @JsonProperty("warehouseLng")
    public Double warehouseLng() {
        return warehouseLongitude;
    }

    @JsonProperty("latitude")
    public Double latitude() {
        return warehouseLatitude;
    }

    @JsonProperty("longitude")
    public Double longitude() {
        return warehouseLongitude;
    }
}
