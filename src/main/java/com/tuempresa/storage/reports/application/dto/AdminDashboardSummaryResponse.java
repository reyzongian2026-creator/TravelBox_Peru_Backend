package com.tuempresa.storage.reports.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminDashboardSummaryResponse(
        String period,
        String periodLabel,
        Instant generatedAt,
        Summary summary,
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
}
