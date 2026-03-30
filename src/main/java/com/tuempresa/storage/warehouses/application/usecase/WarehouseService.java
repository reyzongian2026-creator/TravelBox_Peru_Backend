package com.tuempresa.storage.warehouses.application.usecase;

import com.tuempresa.storage.geo.domain.City;
import com.tuempresa.storage.geo.domain.TouristZone;
import com.tuempresa.storage.geo.infrastructure.out.persistence.CityRepository;
import com.tuempresa.storage.geo.infrastructure.out.persistence.TouristZoneRepository;
import com.tuempresa.storage.shared.application.usecase.AuditLogService;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService.FileCategory;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.shared.infrastructure.web.PublicUrlService;
import com.tuempresa.storage.warehouses.application.dto.AdminWarehouseRequest;
import com.tuempresa.storage.warehouses.application.dto.WarehousePagedResponse;
import com.tuempresa.storage.warehouses.application.dto.WarehouseRegistryResponse;
import com.tuempresa.storage.warehouses.application.dto.WarehouseResponse;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Comparator;
import java.util.List;

@Service
public class WarehouseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WarehouseRepository warehouseRepository;
    private final CityRepository cityRepository;
    private final TouristZoneRepository touristZoneRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final PublicUrlService publicUrlService;

    public WarehouseService(
            WarehouseRepository warehouseRepository,
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            StorageService storageService,
            AuditLogService auditLogService,
            PublicUrlService publicUrlService
    ) {
        this.warehouseRepository = warehouseRepository;
        this.cityRepository = cityRepository;
        this.touristZoneRepository = touristZoneRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.publicUrlService = publicUrlService;
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        Warehouse warehouse = loadWarehouse(id);
        return toResponse(warehouse, null);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> search(Long cityId, String query) {
        return warehouseRepository.search(cityId, normalize(query))
                .stream()
                .map(warehouse -> toResponse(warehouse, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> searchAdmin(Long cityId, String query, Boolean active) {
        return warehouseRepository.searchAdmin(cityId, normalize(query), active)
                .stream()
                .map(warehouse -> toResponse(warehouse, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<WarehouseRegistryResponse> registryPage(
            int page,
            int size,
            Long cityId,
            String query,
            Boolean active
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<WarehouseRegistryResponse> mapped = warehouseRepository
                .searchAdminPage(cityId, normalize(query), active, pageRequest)
                .map(this::toRegistryResponse);
        return PagedResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public PagedResponse<WarehousePagedResponse> pagePage(
            int page,
            int size,
            Long cityId,
            String query,
            Boolean active
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Warehouse> warehousePage = warehouseRepository
                .searchAdminPage(cityId, normalize(query), active, pageRequest);
        
        Page<WarehousePagedResponse> mapped = warehousePage.map(warehouse ->
                new WarehousePagedResponse(
                        warehouse.getId(),
                        warehouse.getName(),
                        warehouse.getCity() != null ? warehouse.getCity().getName() : null,
                        warehouse.isActive(),
                        warehouse.getPhotoPath(),
                        warehouse.getCapacity(),
                        warehouse.getCreatedAt()
                )
        );
        return PagedResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> nearby(double latitude, double longitude, double radiusKm) {
        List<WarehouseDistance> ranked = warehouseRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(warehouse -> {
                    double distance = haversineKm(latitude, longitude, warehouse.getLatitude(), warehouse.getLongitude());
                    return new WarehouseDistance(warehouse, distance);
                })
                .sorted(Comparator.comparingDouble(WarehouseDistance::distanceKm))
                .toList();

        List<WarehouseDistance> inRadius = ranked.stream()
                .filter(result -> result.distanceKm <= radiusKm)
                .toList();

        if (!inRadius.isEmpty()) {
            return inRadius.stream()
                    .map(result -> toResponse(result.warehouse(), result.distanceKm()))
                    .toList();
        }

        return ranked.stream()
                .limit(5)
                .map(result -> toResponse(result.warehouse(), result.distanceKm()))
                .toList();
    }

    @Transactional
    public WarehouseResponse create(AdminWarehouseRequest request) {
        City city = loadCity(request.cityId());
        TouristZone zone = loadZone(request.zoneId());
        Warehouse warehouse = Warehouse.of(
                city,
                zone,
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.capacity(),
                request.openHour(),
                request.closeHour(),
                request.rules(),
                resolveMoney(request.pricePerHourSmall(), Warehouse.DEFAULT_PRICE_SMALL_PER_HOUR),
                resolveMoney(request.pricePerHourMedium(), Warehouse.DEFAULT_PRICE_MEDIUM_PER_HOUR),
                resolveMoney(request.pricePerHourLarge(), Warehouse.DEFAULT_PRICE_LARGE_PER_HOUR),
                resolveMoney(request.pricePerHourExtraLarge(), Warehouse.DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR),
                resolveMoney(request.pickupFee(), Warehouse.DEFAULT_PICKUP_FEE),
                resolveMoney(request.dropoffFee(), Warehouse.DEFAULT_DROPOFF_FEE),
                resolveMoney(request.insuranceFee(), Warehouse.DEFAULT_INSURANCE_FEE)
        );
        boolean active = request.active() == null || request.active();
        if (!active) {
            warehouse.deactivate();
        }
        Warehouse persisted = warehouseRepository.saveAndFlush(warehouse);
        return toResponse(persisted, null);
    }

    @Transactional
    public WarehouseResponse update(Long id, AdminWarehouseRequest request) {
        Warehouse warehouse = loadWarehouse(id);
        City city = loadCity(request.cityId());
        TouristZone zone = loadZone(request.zoneId());
        warehouse.update(
                city,
                zone,
                request.name(),
                request.address(),
                request.latitude(),
                request.longitude(),
                request.capacity(),
                request.openHour(),
                request.closeHour(),
                request.rules(),
                request.active() != null ? request.active() : warehouse.isActive(),
                resolveMoney(request.pricePerHourSmall(), warehouse.getPricePerHourSmall()),
                resolveMoney(request.pricePerHourMedium(), warehouse.getPricePerHourMedium()),
                resolveMoney(request.pricePerHourLarge(), warehouse.getPricePerHourLarge()),
                resolveMoney(request.pricePerHourExtraLarge(), warehouse.getPricePerHourExtraLarge()),
                resolveMoney(request.pickupFee(), warehouse.getPickupFee()),
                resolveMoney(request.dropoffFee(), warehouse.getDropoffFee()),
                resolveMoney(request.insuranceFee(), warehouse.getInsuranceFee())
        );
        String requestedPhotoPath = firstNonBlank(request.imageUrl(), request.coverImageUrl());
        String sanitizedPhotoPath = sanitizeStoredPhotoPath(id, requestedPhotoPath);
        if (sanitizedPhotoPath != null) {
            warehouse.updatePhoto(sanitizedPhotoPath);
        }
        Warehouse persisted = warehouseRepository.saveAndFlush(warehouse);
        return toResponse(persisted, null);
    }

    @Transactional
    public WarehouseResponse updatePhoto(Long id, MultipartFile file) throws Exception {
        Warehouse warehouse = loadWarehouse(id);
        StorageService.UploadResult result = storageService.upload(file, FileCategory.WAREHOUSES);
        auditLogService.logFileUpload(result.filename(), "warehouses", "warehouse-" + id);
        warehouse.updatePhoto(result.url());
        Warehouse persisted = warehouseRepository.saveAndFlush(warehouse);
        return toResponse(persisted, null);
    }

    @Transactional
    public void delete(Long id) {
        Warehouse warehouse = loadWarehouse(id);
        warehouse.deactivate();
    }

    @Transactional(readOnly = true)
    public Warehouse requireWarehouse(Long warehouseId) {
        return requireActiveWarehouse(warehouseId);
    }

    @Transactional
    public Warehouse requireWarehouseForUpdate(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findByIdForUpdate(warehouseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Almacen no encontrado."));
        if (!warehouse.isActive()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Almacen no disponible.");
        }
        return warehouse;
    }

    @Transactional(readOnly = true)
    public Warehouse loadWarehouse(Long warehouseId) {
        return warehouseRepository.findByIdWithLocation(warehouseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Almacen no encontrado."));
    }

    @Transactional(readOnly = true)
    public Warehouse requireActiveWarehouse(Long warehouseId) {
        Warehouse warehouse = loadWarehouse(warehouseId);
        if (!warehouse.isActive()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Almacen no disponible.");
        }
        return warehouse;
    }

    private City loadCity(Long cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CITY_NOT_FOUND", "Ciudad no valida."));
    }

    private TouristZone loadZone(Long zoneId) {
        if (zoneId == null) {
            return null;
        }
        return touristZoneRepository.findById(zoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ZONE_NOT_FOUND", "Zona turistica no valida."));
    }

    private WarehouseResponse toResponse(Warehouse warehouse, Double distanceKm) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCity().getId(),
                warehouse.getCity().getName(),
                warehouse.getZone() != null ? warehouse.getZone().getId() : null,
                warehouse.getZone() != null ? warehouse.getZone().getName() : null,
                warehouse.getLatitude(),
                warehouse.getLongitude(),
                warehouse.getCapacity(),
                warehouse.getOccupied(),
                warehouse.availableSlots(),
                warehouse.isActive(),
                warehouse.getOpenHour(),
                warehouse.getCloseHour(),
                warehouse.getRules(),
                warehouse.getPricePerHourSmall(),
                warehouse.getPricePerHourMedium(),
                warehouse.getPricePerHourLarge(),
                warehouse.getPricePerHourExtraLarge(),
                warehouse.getPickupFee(),
                warehouse.getDropoffFee(),
                warehouse.getInsuranceFee(),
                resolveImageUrl(warehouse),
                distanceKm,
                distanceKm != null && distanceKm <= 2.0,
                !warehouse.hasAvailableCapacity()
        );
    }

    private WarehouseRegistryResponse toRegistryResponse(Warehouse warehouse) {
        return new WarehouseRegistryResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCity().getId(),
                warehouse.getCity().getName(),
                warehouse.getLatitude(),
                warehouse.getLongitude(),
                warehouse.getCapacity(),
                warehouse.getOccupied(),
                warehouse.availableSlots(),
                warehouse.isActive(),
                warehouse.getOpenHour(),
                warehouse.getCloseHour(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }

    private int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 20;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.trim();
    }

    private BigDecimal resolveMoney(BigDecimal requested, BigDecimal fallback) {
        BigDecimal resolved = requested != null ? requested : fallback;
        if (resolved.signum() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PRICE_CONFIG",
                    "Los montos del almacen no pueden ser negativos."
            );
        }
        return resolved.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveImageUrl(Warehouse warehouse) {
        long version = warehouse.getUpdatedAt() != null
                ? warehouse.getUpdatedAt().toEpochMilli()
                : warehouse.getId() != null ? warehouse.getId() : 0L;
        String path = sanitizeStoredPhotoPath(warehouse.getId(), warehouse.getPhotoPath());
        if (path == null || path.isBlank()) {
            path = "/api/v1/warehouses/" + warehouse.getId() + "/image";
        }
        String separator = path.contains("?") ? "&" : "?";
        return normalizeUrl(publicUrlService.absolute(path + separator + "v=" + version));
    }

    private String sanitizeStoredPhotoPath(Long warehouseId, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String trimmed = rawPath.trim();
        String lower = trimmed.toLowerCase();
        String selfApiFragment = "/api/v1/warehouses/" + warehouseId + "/image";
        if (lower.contains(selfApiFragment)) {
            return null;
        }

        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            if (path != null && path.toLowerCase().contains(selfApiFragment)) {
                return null;
            }
            if (path != null && path.startsWith("/api/v1/")) {
                trimmed = path;
            }
        } catch (IllegalArgumentException ignored) {
            // Keep raw path if it's not a full URI and not self-referential.
        }

        return trimmed.replaceAll("([?&])v=[^&]*", "").replaceAll("[?&]+$", "");
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return null;
    }

    private static String normalizeUrl(String url) {
        if (url == null) return null;
        return url.replace(":/", "://").replaceAll("(?<!:)/{2,}", "/");
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                   + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                     * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private record WarehouseDistance(Warehouse warehouse, double distanceKm) {
    }
}
