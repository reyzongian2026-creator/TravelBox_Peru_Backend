package com.tuempresa.storage.geo.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.tuempresa.storage.geo.application.dto.RoutePointResponse;
import com.tuempresa.storage.geo.application.dto.RouteResponse;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GeoRoutingService {

    private final RestClient osrmRestClient;
    private final RestClient googleRestClient;
    private final RestClient azureMapsRestClient;
    private final String provider;
    private final boolean allowMockFallback;
    private final String defaultProfile;
    private final String googleApiKey;
    private final String googleFieldMask;
    private final String azureApiKey;

    public GeoRoutingService(
            RestClient.Builder restClientBuilder,
            @Value("${app.routing.provider:mock}") String provider,
            @Value("${app.routing.allow-mock-fallback:true}") boolean allowMockFallback,
            @Value("${app.routing.osrm.base-url:https://router.project-osrm.org}") String osrmBaseUrl,
            @Value("${app.routing.osrm.profile:driving}") String defaultProfile,
            @Value("${app.routing.google.base-url:https://routes.googleapis.com}") String googleBaseUrl,
            @Value("${app.routing.google.api-key:}") String googleApiKey,
            @Value("${app.routing.google.field-mask:routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline}") String googleFieldMask,
            @Value("${app.routing.azure.base-url:https://atlas.microsoft.com}") String azureBaseUrl,
            @Value("${app.routing.azure.api-key:}") String azureApiKey
    ) {
        this.osrmRestClient = restClientBuilder.baseUrl(osrmBaseUrl).build();
        this.googleRestClient = restClientBuilder.baseUrl(googleBaseUrl).build();
        this.azureMapsRestClient = restClientBuilder.baseUrl(azureBaseUrl).build();
        this.provider = provider == null ? "mock" : provider.trim().toLowerCase(Locale.ROOT);
        this.allowMockFallback = allowMockFallback;
        this.defaultProfile = normalizeProfile(defaultProfile);
        this.googleApiKey = googleApiKey == null ? "" : googleApiKey.trim();
        this.googleFieldMask = StringUtils.hasText(googleFieldMask)
                ? googleFieldMask.trim()
                : "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline";
        this.azureApiKey = azureApiKey == null ? "" : azureApiKey.trim();
    }

    public RouteResponse route(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            String profile
    ) {
        String effectiveProfile = normalizeProfile(profile);
        if (!"mock".equals(provider)) {
            try {
                RouteResponse realRoute;
                if (isGoogleProvider(provider)) {
                    realRoute = fetchGoogleRoute(
                            originLatitude,
                            originLongitude,
                            destinationLatitude,
                            destinationLongitude,
                            effectiveProfile
                    );
                } else if (isAzureProvider(provider)) {
                    realRoute = fetchAzureRoute(
                            originLatitude,
                            originLongitude,
                            destinationLatitude,
                            destinationLongitude,
                            effectiveProfile
                    );
                } else {
                    realRoute = fetchOsrmRoute(
                            originLatitude,
                            originLongitude,
                            destinationLatitude,
                            destinationLongitude,
                            effectiveProfile
                    );
                }
                if (realRoute != null && !realRoute.points().isEmpty()) {
                    return realRoute;
                }
            } catch (RestClientException | IllegalStateException ex) {
                if (!allowMockFallback) {
                    throw new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "ROUTING_PROVIDER_UNAVAILABLE",
                            "El proveedor de rutas no esta disponible y el fallback mock esta deshabilitado."
                    );
                }
            }
        } else if (!allowMockFallback) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ROUTING_PROVIDER_MOCK_DISABLED",
                    "El proveedor de rutas mock esta deshabilitado en este entorno."
            );
        }
        if (!allowMockFallback) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ROUTING_FALLBACK_DISABLED",
                    "No se pudo resolver la ruta y el fallback mock esta deshabilitado."
            );
        }
        return fallbackRoute(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude
        );
    }

    private RouteResponse fetchOsrmRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            String profile
    ) {
        String path = "/route/v1/%s/%s,%s;%s,%s".formatted(
                profile,
                trimCoordinate(originLongitude),
                trimCoordinate(originLatitude),
                trimCoordinate(destinationLongitude),
                trimCoordinate(destinationLatitude)
        );

        JsonNode response = osrmRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("overview", "full")
                        .queryParam("geometries", "geojson")
                        .queryParam("steps", "false")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode routes = response == null ? null : response.path("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw new IllegalStateException("No route returned by provider.");
        }
        JsonNode route = routes.get(0);
        JsonNode coordinates = route.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new IllegalStateException("Route geometry is empty.");
        }

        List<RoutePointResponse> points = new ArrayList<>();
        for (JsonNode coordinate : coordinates) {
            if (!coordinate.isArray() || coordinate.size() < 2) {
                continue;
            }
            points.add(new RoutePointResponse(
                    coordinate.get(1).asDouble(),
                    coordinate.get(0).asDouble()
            ));
        }
        if (points.size() < 2) {
            throw new IllegalStateException("Route has insufficient points.");
        }

        return new RouteResponse(
                "osrm",
                false,
                route.path("distance").asDouble(estimateDistanceMeters(points)),
                route.path("duration").asDouble(estimateDurationSeconds(points)),
                points
        );
    }

    private RouteResponse fetchGoogleRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            String profile
    ) {
        if (!StringUtils.hasText(googleApiKey)) {
            throw new IllegalStateException("Google routing API key is empty.");
        }

        Map<String, Object> requestBody = Map.of(
                "origin", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", originLatitude,
                                        "longitude", originLongitude
                                )
                        )
                ),
                "destination", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", destinationLatitude,
                                        "longitude", destinationLongitude
                                )
                        )
                ),
                "travelMode", toGoogleTravelMode(profile),
                "computeAlternativeRoutes", false
        );

        JsonNode response = googleRestClient.post()
                .uri("/directions/v2:computeRoutes")
                .header("X-Goog-Api-Key", googleApiKey)
                .header("X-Goog-FieldMask", googleFieldMask)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        JsonNode routes = response == null ? null : response.path("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw new IllegalStateException("No route returned by provider.");
        }
        JsonNode route = routes.get(0);
        String encodedPolyline = route.path("polyline").path("encodedPolyline").asText("");
        if (!StringUtils.hasText(encodedPolyline)) {
            throw new IllegalStateException("Route polyline is empty.");
        }

        List<RoutePointResponse> points = decodePolyline(encodedPolyline);
        if (points.size() < 2) {
            throw new IllegalStateException("Route has insufficient points.");
        }

        double distanceMeters = route.path("distanceMeters").asDouble(estimateDistanceMeters(points));
        double durationSeconds = parseGoogleDurationSeconds(route.path("duration").asText(""));
        if (durationSeconds <= 0) {
            durationSeconds = estimateDurationSeconds(points);
        }

        return new RouteResponse(
                "google",
                false,
                distanceMeters,
                durationSeconds,
                points
        );
    }

    private RouteResponse fallbackRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        List<RoutePointResponse> points = buildCurvedFallbackPoints(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude
        );
        return new RouteResponse(
                "mock-curved",
                true,
                estimateDistanceMeters(points),
                estimateDurationSeconds(points),
                points
        );
    }

    private List<RoutePointResponse> buildCurvedFallbackPoints(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        List<RoutePointResponse> points = new ArrayList<>();
        double deltaLat = destinationLatitude - originLatitude;
        double deltaLng = destinationLongitude - originLongitude;
        double lateralLat = -deltaLng * 0.22;
        double lateralLng = deltaLat * 0.22;
        double controlLatitude = ((originLatitude + destinationLatitude) / 2.0) + lateralLat;
        double controlLongitude = ((originLongitude + destinationLongitude) / 2.0) + lateralLng;

        for (int step = 0; step <= 10; step++) {
            double t = step / 10.0;
            double latitude = quadraticBezier(originLatitude, controlLatitude, destinationLatitude, t);
            double longitude = quadraticBezier(originLongitude, controlLongitude, destinationLongitude, t);
            points.add(new RoutePointResponse(latitude, longitude));
        }
        return points;
    }

    private double quadraticBezier(double start, double control, double end, double t) {
        double inverse = 1 - t;
        return (inverse * inverse * start) + (2 * inverse * t * control) + (t * t * end);
    }

    private double estimateDistanceMeters(List<RoutePointResponse> points) {
        if (points.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int index = 1; index < points.size(); index++) {
            RoutePointResponse previous = points.get(index - 1);
            RoutePointResponse current = points.get(index);
            total += haversineMeters(previous.latitude(), previous.longitude(), current.latitude(), current.longitude());
        }
        return total;
    }

    private double estimateDurationSeconds(List<RoutePointResponse> points) {
        double distanceMeters = estimateDistanceMeters(points);
        double averageMetersPerSecond = 7.5;
        return distanceMeters / averageMetersPerSecond;
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000;
        double deltaLatitude = Math.toRadians(lat2 - lat1);
        double deltaLongitude = Math.toRadians(lon2 - lon1);
        double sinLat = Math.sin(deltaLatitude / 2);
        double sinLng = Math.sin(deltaLongitude / 2);
        double a = (sinLat * sinLat)
                + (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLng * sinLng);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private String normalizeProfile(String rawProfile) {
        String normalized = StringUtils.hasText(rawProfile)
                ? rawProfile.trim().toLowerCase(Locale.ROOT)
                : defaultProfile;
        return switch (normalized) {
            case "foot", "walking" -> "foot";
            case "bike", "cycling" -> "bike";
            default -> "driving";
        };
    }

    private boolean isGoogleProvider(String rawProvider) {
        return switch (rawProvider) {
            case "google", "googlemaps", "google-maps", "google_maps", "routes" -> true;
            default -> false;
        };
    }

    private boolean isAzureProvider(String rawProvider) {
        return switch (rawProvider) {
            case "azure", "azuremaps", "azure-maps", "azure_maps", "atlas" -> true;
            default -> false;
        };
    }

    private RouteResponse fetchAzureRoute(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            String profile
    ) {
        if (!StringUtils.hasText(azureApiKey)) {
            throw new IllegalStateException("Azure Maps API key is empty.");
        }

        String travelMode = toAzureTravelMode(profile);
        JsonNode response = azureMapsRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/route/directions/json")
                        .queryParam("api-version", "1.0")
                        .queryParam("subscription-key", azureApiKey)
                        .queryParam(
                                "query",
                                trimCoordinate(originLatitude) + "," + trimCoordinate(originLongitude)
                                        + ":" + trimCoordinate(destinationLatitude) + "," + trimCoordinate(destinationLongitude)
                        )
                        .queryParam("routeType", "fastest")
                        .queryParam("travelMode", travelMode)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode routes = response == null ? null : response.path("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            throw new IllegalStateException("No route returned by Azure Maps.");
        }

        JsonNode route = routes.get(0);
        JsonNode legs = route.path("legs");
        if (legs == null || !legs.isArray() || legs.isEmpty()) {
            throw new IllegalStateException("Route has no legs.");
        }

        List<RoutePointResponse> points = new ArrayList<>();
        double totalDistanceMeters = 0;
        double totalDurationSeconds = 0;

        for (JsonNode leg : legs) {
            JsonNode pointsArray = leg.path("points");
            if (pointsArray != null && pointsArray.isArray()) {
                for (JsonNode point : pointsArray) {
                    double lat = point.path("latitude").asDouble();
                    double lon = point.path("longitude").asDouble();
                    points.add(new RoutePointResponse(lat, lon));
                }
            }
            totalDistanceMeters += leg.path("summary").path("lengthInMeters").asDouble(0);
            totalDurationSeconds += readAzureDurationSeconds(leg.path("summary").path("travelTimeInSeconds"));
        }

        if (points.size() < 2) {
            throw new IllegalStateException("Route has insufficient points.");
        }

        if (totalDistanceMeters <= 0) {
            totalDistanceMeters = estimateDistanceMeters(points);
        }
        if (totalDurationSeconds <= 0) {
            totalDurationSeconds = estimateDurationSeconds(points);
        }

        return new RouteResponse(
                "azure",
                false,
                totalDistanceMeters,
                totalDurationSeconds,
                points
        );
    }

    private String toAzureTravelMode(String profile) {
        return switch (profile) {
            case "foot" -> "pedestrian";
            case "bike" -> "bicycle";
            default -> "car";
        };
    }

    private double parseAzureDurationSeconds(String rawDuration) {
        if (!StringUtils.hasText(rawDuration)) {
            return 0;
        }
        String normalized = rawDuration.trim();
        if (normalized.endsWith("s") || normalized.endsWith("S")) {
            try {
                return Double.parseDouble(normalized.substring(0, normalized.length() - 1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private double readAzureDurationSeconds(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asDouble(0);
        }
        return parseAzureDurationSeconds(node.asText(""));
    }

    private String toGoogleTravelMode(String profile) {
        return switch (profile) {
            case "foot" -> "WALK";
            case "bike" -> "BICYCLE";
            default -> "DRIVE";
        };
    }

    private List<RoutePointResponse> decodePolyline(String encodedPolyline) {
        List<RoutePointResponse> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;

        while (index < encodedPolyline.length()) {
            latitude += decodeCoordinateDelta(encodedPolyline, index);
            index = nextPolylineIndex(encodedPolyline, index);
            longitude += decodeCoordinateDelta(encodedPolyline, index);
            index = nextPolylineIndex(encodedPolyline, index);
            points.add(new RoutePointResponse(latitude / 100_000.0, longitude / 100_000.0));
        }
        return points;
    }

    private int decodeCoordinateDelta(String encodedPolyline, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;

        while (true) {
            if (index >= encodedPolyline.length()) {
                throw new IllegalStateException("Invalid encoded polyline.");
            }
            int chunk = encodedPolyline.charAt(index++) - 63;
            result |= (chunk & 0x1f) << shift;
            shift += 5;
            if (chunk < 0x20) {
                break;
            }
        }
        return (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
    }

    private int nextPolylineIndex(String encodedPolyline, int startIndex) {
        int index = startIndex;
        while (index < encodedPolyline.length()) {
            int chunk = encodedPolyline.charAt(index++) - 63;
            if (chunk < 0x20) {
                return index;
            }
        }
        throw new IllegalStateException("Invalid encoded polyline.");
    }

    private double parseGoogleDurationSeconds(String rawDuration) {
        if (!StringUtils.hasText(rawDuration)) {
            return 0;
        }
        String normalized = rawDuration.trim();
        if (normalized.endsWith("s") || normalized.endsWith("S")) {
            try {
                return Double.parseDouble(normalized.substring(0, normalized.length() - 1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String trimCoordinate(double value) {
        return String.format(Locale.US, "%.6f", value);
    }
}
