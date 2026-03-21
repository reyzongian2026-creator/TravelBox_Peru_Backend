package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

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

    public static List<String> headers() {
        return List.of(
                "ID", "QR Code", "User ID", "User Name", "User Email",
                "Warehouse ID", "Warehouse Name", "City",
                "Start At", "End At", "Status", "Total Price",
                "Items", "Bag Size", "Pickup", "Dropoff", "Insurance", "Created At"
        );
    }
}
