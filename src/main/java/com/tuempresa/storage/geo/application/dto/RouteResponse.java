package com.tuempresa.storage.geo.application.dto;

import java.util.List;

public record RouteResponse(
        String provider,
        boolean fallbackUsed,
        double distanceMeters,
        double durationSeconds,
        List<RoutePointResponse> points
) {
}
