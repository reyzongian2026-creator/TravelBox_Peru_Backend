package com.tuempresa.storage.reports.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminDashboardResponse(
        String period,
        String periodLabel,
        Instant generatedAt,
        Summary summary,
        List<TrendPoint> trend,
        List<StatusBreakdown> statusBreakdown,
        List<WarehousePerformance> topWarehouses,
        WarehousePerformance bestWarehouse,
        List<CityPerformance> topCities,
        List<OperationalUserPerformance> topCouriers,
        List<OperationalUserPerformance> topOperators,
        long totalUsers,
        long totalWarehouses,
        long activeReservations,
        long openIncidents,
        BigDecimal confirmedPaymentsAmount
) {
    public record Summary(
            long reservations,
            long activeReservations,
            long completedReservations,
            long cancelledReservations,
            long incidentReservations,
            long pendingPaymentReservations,
            long uniqueClients,
            long openIncidents,
            BigDecimal confirmedRevenue,
            BigDecimal averageTicket,
            double completionRate,
            double cancellationRate
    ) {
    }

    public record TrendPoint(
            String label,
            long reservations,
            long incidents,
            BigDecimal confirmedRevenue
    ) {
    }

    public record StatusBreakdown(
            String status,
            String label,
            long count
    ) {
    }

    public record WarehousePerformance(
            Long warehouseId,
            String warehouseName,
            String city,
            String zone,
            long interactionCount,
            long completedReservations,
            long cancelledReservations,
            long incidentCount,
            BigDecimal confirmedRevenue
    ) {
    }

    public record CityPerformance(
            String city,
            long interactionCount,
            long completedReservations,
            long incidentCount,
            BigDecimal confirmedRevenue
    ) {
    }

    public record OperationalUserPerformance(
            Long userId,
            String fullName,
            String email,
            long deliveryCreatedCount,
            long deliveryAssignedCount,
            long deliveryCompletedCount,
            long activeDeliveryCount
    ) {
    }
}
