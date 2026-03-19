package com.tuempresa.storage.geo.application.dto;

public record GeoSearchSuggestionResponse(
        String type,
        Long id,
        String label,
        Long cityId,
        String cityName,
        Double latitude,
        Double longitude
) {
}
