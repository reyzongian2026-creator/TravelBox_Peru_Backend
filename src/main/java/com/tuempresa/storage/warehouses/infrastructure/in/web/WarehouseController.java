package com.tuempresa.storage.warehouses.infrastructure.in.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.warehouses.application.dto.WarehouseAvailabilityResponse;
import com.tuempresa.storage.warehouses.application.dto.WarehouseResponse;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseAvailabilityService;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseImageService;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    private final WarehouseService warehouseService;
    private final WarehouseAvailabilityService warehouseAvailabilityService;
    private final WarehouseImageService warehouseImageService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public WarehouseController(
            WarehouseService warehouseService,
            WarehouseAvailabilityService warehouseAvailabilityService,
            WarehouseImageService warehouseImageService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.warehouseService = warehouseService;
        this.warehouseAvailabilityService = warehouseAvailabilityService;
        this.warehouseImageService = warehouseImageService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/{id}")
    public Mono<WarehouseResponse> detail(@PathVariable Long id) {
        return reactiveBlockingExecutor.call(() -> warehouseService.findById(id));
    }

    @GetMapping(value = "/{id}/image")
    @Transactional(readOnly = true)
    public Mono<ResponseEntity<byte[]>> image(@PathVariable Long id) {
        return reactiveBlockingExecutor.call(() -> {
            WarehouseImageService.WarehouseImageContent image = warehouseImageService.loadImage(warehouseService.loadWarehouse(id));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                    .contentType(image.mediaType())
                    .body(image.bytes());
        });
    }

    @GetMapping("/search")
    public Mono<List<WarehouseResponse>> search(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String query
    ) {
        return reactiveBlockingExecutor.call(() -> warehouseService.search(cityId, query));
    }

    @GetMapping("/nearby")
    public Mono<List<WarehouseResponse>> nearby(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "5") double radiusKm
    ) {
        Double resolvedLat = lat != null ? lat : latitude;
        Double resolvedLng = lng != null ? lng : longitude;
        if (resolvedLat == null || resolvedLng == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GEO_COORDS_REQUIRED", "Debes enviar lat/lng o latitude/longitude.");
        }
        Double finalLat = resolvedLat;
        Double finalLng = resolvedLng;
        return reactiveBlockingExecutor.call(() -> warehouseService.nearby(finalLat, finalLng, radiusKm));
    }

    @GetMapping("/{id}/availability")
    public Mono<WarehouseAvailabilityResponse> availability(
            @PathVariable Long id,
            @RequestParam String startAt,
            @RequestParam String endAt
    ) {
        Instant parsedStartAt = parseFlexibleInstant(startAt);
        Instant parsedEndAt = parseFlexibleInstant(endAt);
        return reactiveBlockingExecutor.call(
                () -> warehouseAvailabilityService.availabilityByWarehouse(id, parsedStartAt, parsedEndAt)
        );
    }

    @GetMapping({"/availability/search", "/disponibilidad/buscar"})
    public Mono<List<WarehouseAvailabilityResponse>> availabilitySearch(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String query,
            @RequestParam String startAt,
            @RequestParam String endAt
    ) {
        Instant parsedStartAt = parseFlexibleInstant(startAt);
        Instant parsedEndAt = parseFlexibleInstant(endAt);
        return reactiveBlockingExecutor.call(
                () -> warehouseAvailabilityService.searchAvailability(
                        cityId,
                        query,
                        parsedStartAt,
                        parsedEndAt
                )
        );
    }

    private Instant parseFlexibleInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_TIME", "Debes enviar startAt/endAt en formato ISO.");
        }
        String value = rawValue.trim();

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(value).atZone(DEFAULT_ZONE).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DATE_TIME",
                "Fecha invalida. Usa ISO, por ejemplo 2026-03-12T03:00:00Z o 2026-03-12T03:00:00.000"
        );
    }
}
