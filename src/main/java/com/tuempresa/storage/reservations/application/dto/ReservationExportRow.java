package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.reservations.domain.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public record ReservationExportRow(
        Long id,
        String qrCode,
        Long userId,
        String userName,
        String userEmail,
        Long warehouseId,
        String warehouseName,
        String warehouseCity,
        Instant startAt,
        Instant endAt,
        String status,
        BigDecimal totalPrice,
        int estimatedItems,
        String bagSize,
        boolean pickupRequested,
        boolean dropoffRequested,
        boolean extraInsurance,
        Instant createdAt
) {
    public static List<Function<Reservation, String>> columnMappers() {
        return List.of(
                r -> String.valueOf(r.getId()),
                r -> r.getQrCode() != null ? r.getQrCode() : "",
                r -> String.valueOf(r.getUser().getId()),
                r -> r.getUser().getFullName(),
                r -> r.getUser().getEmail() != null ? r.getUser().getEmail() : "",
                r -> String.valueOf(r.getWarehouse().getId()),
                r -> r.getWarehouse().getName(),
                r -> r.getWarehouse().getCity().getName(),
                r -> r.getStartAt() != null ? r.getStartAt().toString() : "",
                r -> r.getEndAt() != null ? r.getEndAt().toString() : "",
                r -> r.getStatus().name(),
                r -> r.getTotalPrice() != null ? r.getTotalPrice().toString() : "0.00",
                r -> String.valueOf(r.getEstimatedItems()),
                r -> r.getBagSize() != null ? r.getBagSize().code() : "",
                r -> r.isPickupRequested() ? "Si" : "No",
                r -> r.isDropoffRequested() ? "Si" : "No",
                r -> r.isExtraInsurance() ? "Si" : "No",
                r -> r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
        );
    }

    public static List<Function<ReservationExportRow, String>> dtoColumnMappers() {
        return List.of(
                r -> String.valueOf(r.id()),
                r -> r.qrCode() != null ? r.qrCode() : "",
                r -> String.valueOf(r.userId()),
                r -> r.userName() != null ? r.userName() : "",
                r -> r.userEmail() != null ? r.userEmail() : "",
                r -> String.valueOf(r.warehouseId()),
                r -> r.warehouseName() != null ? r.warehouseName() : "",
                r -> r.warehouseCity() != null ? r.warehouseCity() : "",
                r -> r.startAt() != null ? r.startAt().toString() : "",
                r -> r.endAt() != null ? r.endAt().toString() : "",
                r -> r.status() != null ? r.status() : "",
                r -> r.totalPrice() != null ? r.totalPrice().toString() : "0.00",
                r -> String.valueOf(r.estimatedItems()),
                r -> r.bagSize() != null ? r.bagSize() : "",
                r -> r.pickupRequested() ? "Si" : "No",
                r -> r.dropoffRequested() ? "Si" : "No",
                r -> r.extraInsurance() ? "Si" : "No",
                r -> r.createdAt() != null ? r.createdAt().toString() : ""
        );
    }

    public static List<String> headers() {
        return List.of(
                "ID", "QR Code", "User ID", "User Name", "User Email",
                "Warehouse ID", "Warehouse Name", "City",
                "Start At", "End At", "Status", "Total Price",
                "Items", "Bag Size", "Pickup", "Dropoff", "Insurance", "Created At"
        );
    }
}
