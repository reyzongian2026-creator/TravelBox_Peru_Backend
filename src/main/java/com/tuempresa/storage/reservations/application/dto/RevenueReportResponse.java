package com.tuempresa.storage.reservations.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RevenueReportResponse(
        BigDecimal totalRevenue,
        long totalReservations,
        BigDecimal averageReservationValue,
        Instant periodStart,
        Instant periodEnd,
        String periodLabel,
        List<RevenueByWarehouse> byWarehouse,
        List<RevenueByCity> byCity,
        List<RevenueByDay> byDay
) {
    public record RevenueByWarehouse(
            Long warehouseId,
            String warehouseName,
            BigDecimal revenue,
            long reservationCount
    ) {}

    public record RevenueByCity(
            String cityName,
            BigDecimal revenue,
            long reservationCount
    ) {}

    public record RevenueByDay(
            String date,
            BigDecimal revenue,
            long reservationCount
    ) {}
}
