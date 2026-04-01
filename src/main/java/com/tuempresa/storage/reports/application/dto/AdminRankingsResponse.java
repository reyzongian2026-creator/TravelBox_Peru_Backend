package com.tuempresa.storage.reports.application.dto;

import java.time.Instant;
import java.util.List;

public record AdminRankingsResponse(
        String period,
        String periodLabel,
        Instant generatedAt,
        List<RankingItem> warehouseRanking,
        List<RankingItem> cityRanking,
        List<RankingItem> courierRanking,
        List<RankingItem> operatorRanking
) {
    public record RankingItem(
            int rank,
            String id,
            String name,
            long value,
            double percentage
    ) {
    }
}
