package com.tuempresa.storage.geo.application.dto;

public record TouristZoneResponse(
        Long id,
        Long cityId,
        String name,
        double latitude,
        double longitude,
        double radiusKm
) {
}
