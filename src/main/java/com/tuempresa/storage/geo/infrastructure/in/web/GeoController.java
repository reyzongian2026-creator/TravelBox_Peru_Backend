package com.tuempresa.storage.geo.infrastructure.in.web;

import com.tuempresa.storage.geo.application.dto.CityResponse;
import com.tuempresa.storage.geo.application.dto.GeoSearchSuggestionResponse;
import com.tuempresa.storage.geo.application.dto.RouteResponse;
import com.tuempresa.storage.geo.application.dto.TouristZoneResponse;
import com.tuempresa.storage.geo.application.usecase.GeoQueryService;
import com.tuempresa.storage.geo.application.usecase.GeoRoutingService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.warehouses.application.dto.WarehouseResponse;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/geo")
public class GeoController {

    private final GeoQueryService geoQueryService;
    private final GeoRoutingService geoRoutingService;
    private final WarehouseService warehouseService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public GeoController(
            GeoQueryService geoQueryService,
            GeoRoutingService geoRoutingService,
            WarehouseService warehouseService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.geoQueryService = geoQueryService;
        this.geoRoutingService = geoRoutingService;
        this.warehouseService = warehouseService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/cities")
    public Mono<List<CityResponse>> cities() {
        return reactiveBlockingExecutor.call(geoQueryService::listCities);
    }

    @GetMapping("/zones")
    public Mono<List<TouristZoneResponse>> zones(@RequestParam @NotNull Long cityId) {
        return reactiveBlockingExecutor.call(() -> geoQueryService.listZones(cityId));
    }

    @GetMapping("/warehouses/nearby")
    public Mono<List<WarehouseResponse>> nearby(
            @RequestParam(required = false) @Min(-90) @Max(90) Double lat,
            @RequestParam(required = false) @Min(-90) @Max(90) Double latitude,
            @RequestParam(required = false) @Min(-180) @Max(180) Double lng,
            @RequestParam(required = false) @Min(-180) @Max(180) Double longitude,
            @RequestParam(defaultValue = "5") @Min(0) double radiusKm
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

    @GetMapping({"/warehouses/search", "/warehouses/quick-search"})
    public Mono<List<WarehouseResponse>> warehouseSearch(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String query
    ) {
        return reactiveBlockingExecutor.call(() -> warehouseService.search(cityId, query));
    }

    @GetMapping({"/search", "/quick-search", "/suggestions"})
    public Mono<List<GeoSearchSuggestionResponse>> search(@RequestParam String query) {
        return reactiveBlockingExecutor.call(() -> geoQueryService.search(query));
    }

    @GetMapping("/route")
    public Mono<RouteResponse> route(
            @RequestParam @NotNull @Min(-90) @Max(90) Double originLat,
            @RequestParam @NotNull @Min(-180) @Max(180) Double originLng,
            @RequestParam @NotNull @Min(-90) @Max(90) Double destinationLat,
            @RequestParam @NotNull @Min(-180) @Max(180) Double destinationLng,
            @RequestParam(defaultValue = "driving") String profile
    ) {
        return reactiveBlockingExecutor.call(
                () -> geoRoutingService.route(originLat, originLng, destinationLat, destinationLng, profile)
        );
    }
}
