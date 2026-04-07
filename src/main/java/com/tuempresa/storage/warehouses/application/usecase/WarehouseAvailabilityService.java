package com.tuempresa.storage.warehouses.application.usecase;

import com.tuempresa.storage.reservations.domain.ReservationAvailabilityRules;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.warehouses.application.dto.WarehouseAvailabilityResponse;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WarehouseAvailabilityService {

    private final WarehouseRepository warehouseRepository;
    private final ReservationRepository reservationRepository;

    public WarehouseAvailabilityService(
            WarehouseRepository warehouseRepository,
            ReservationRepository reservationRepository
    ) {
        this.warehouseRepository = warehouseRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public WarehouseAvailabilityResponse availabilityByWarehouse(
            Long warehouseId,
            Instant startAt,
            Instant endAt
    ) {
        validateRange(startAt, endAt);
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(Warehouse::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Almacén no encontrado."));
        return buildResponse(warehouse, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public List<WarehouseAvailabilityResponse> searchAvailability(
            Long cityId,
            String query,
            Instant startAt,
            Instant endAt
    ) {
        validateRange(startAt, endAt);
        return warehouseRepository.search(cityId, normalize(query), PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "name")))
                .getContent()
                .stream()
                .map(warehouse -> buildResponse(warehouse, startAt, endAt))
                .toList();
    }

    private WarehouseAvailabilityResponse buildResponse(
            Warehouse warehouse,
            Instant startAt,
            Instant endAt
    ) {
        long reserved = reservationRepository.countOverlapping(
                warehouse.getId(),
                startAt,
                endAt,
                ReservationAvailabilityRules.OCCUPYING_STATES
        );
        int available = Math.max(warehouse.getCapacity() - Math.toIntExact(Math.min(Integer.MAX_VALUE, reserved)), 0);
        return new WarehouseAvailabilityResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCity().getId(),
                warehouse.getCity().getName(),
                startAt,
                endAt,
                warehouse.getCapacity(),
                reserved,
                available,
                available > 0
        );
    }

    private void validateRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Debes enviar startAt y endAt.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "endAt debe ser mayor que startAt.");
        }
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.trim();
    }
}
