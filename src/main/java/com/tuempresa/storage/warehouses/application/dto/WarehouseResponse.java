package com.tuempresa.storage.warehouses.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

public record WarehouseResponse(
        Long id,
        String name,
        String address,
        Long cityId,
        String cityName,
        Long zoneId,
        String zoneName,
        double latitude,
        double longitude,
        int capacity,
        int occupied,
        int available,
        boolean active,
        String openHour,
        String closeHour,
        String rules,
        BigDecimal pricePerHourSmall,
        BigDecimal pricePerHourMedium,
        BigDecimal pricePerHourLarge,
        BigDecimal pricePerHourExtraLarge,
        BigDecimal pickupFee,
        BigDecimal dropoffFee,
        BigDecimal insuranceFee,
        String imageUrl,
        Double distanceKm,
        boolean nearUser,
        boolean noCupo
) {
    @JsonProperty("lat")
    public double lat() {
        return latitude;
    }

    @JsonProperty("lng")
    public double lng() {
        return longitude;
    }

    @JsonProperty("priceFromPerHour")
    public BigDecimal priceFromPerHour() {
        return pricePerHourMedium;
    }

    @JsonProperty("hourlyRate")
    public BigDecimal hourlyRate() {
        return pricePerHourMedium;
    }

    @JsonProperty("sizeRates")
    public Map<String, BigDecimal> sizeRates() {
        return Map.of(
                "S", pricePerHourSmall,
                "M", pricePerHourMedium,
                "L", pricePerHourLarge,
                "XL", pricePerHourExtraLarge
        );
    }

    @JsonProperty("pickupDeliveryFee")
    public BigDecimal pickupDeliveryFee() {
        return pickupFee;
    }

    @JsonProperty("dropoffDeliveryFee")
    public BigDecimal dropoffDeliveryFee() {
        return dropoffFee;
    }

    @JsonProperty("photoUrl")
    public String photoUrl() {
        return imageUrl;
    }

    @JsonProperty("coverImageUrl")
    public String coverImageUrl() {
        return imageUrl;
    }

    @JsonProperty("image")
    public String image() {
        return imageUrl;
    }

    @JsonProperty("imagen")
    public String imagen() {
        return imageUrl;
    }
}
