package com.tuempresa.storage.reports.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminTrendsResponse(
        String period,
        String periodLabel,
        Instant generatedAt,
        List<TrendPoint> dailyTrend,
        List<TrendPoint> weeklyTrend
) {
    public record TrendPoint(
            String label,
            long reservations,
            long incidents,
            BigDecimal confirmedRevenue
    ) {
    }
}
