package com.tuempresa.storage.reports.infrastructure.in.web;

import com.tuempresa.storage.reports.application.dto.AdminDashboardResponse;
import com.tuempresa.storage.reports.application.dto.AdminDashboardSummaryResponse;
import com.tuempresa.storage.reports.application.dto.AdminRankingsResponse;
import com.tuempresa.storage.reports.application.dto.AdminTrendsResponse;
import com.tuempresa.storage.reports.application.usecase.AdminDashboardService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "Dashboard analytics and statistics for administrators")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public AdminDashboardController(
            AdminDashboardService adminDashboardService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.adminDashboardService = adminDashboardService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping({"/dashboard", "/dashboard/summary", "/stats", "/overview"})
    @Operation(summary = "Get dashboard data", description = "Returns complete dashboard with stats, rankings, and trends")
    public Mono<ResponseEntity<AdminDashboardResponse>> dashboard(
            @Parameter(description = "Period: day, week, month, year") @RequestParam(defaultValue = "month") String period
    ) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.dashboard(period))
                .map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                        .body(response));
    }

    @GetMapping("/dashboard/summary-only")
    @Operation(summary = "Get dashboard summary only")
    public Mono<ResponseEntity<AdminDashboardSummaryResponse>> getSummary(
            @Parameter(description = "Period: day, week, month, year") @RequestParam(defaultValue = "month") String period
    ) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.buildSummary(period))
                .map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                        .body(response));
    }

    @GetMapping("/dashboard/rankings-only")
    @Operation(summary = "Get top rankings", description = "Returns top warehouses, cities, couriers, and operators")
    public Mono<ResponseEntity<AdminRankingsResponse>> getRankings(
            @Parameter(description = "Period: day, week, month, year") @RequestParam(defaultValue = "month") String period,
            @Parameter(description = "Maximum items per category") @RequestParam(defaultValue = "10") int limit
    ) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.buildRankings(period, limit))
                .map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                        .body(response));
    }

    @GetMapping("/dashboard/trends-only")
    @Operation(summary = "Get trend data")
    public Mono<ResponseEntity<AdminTrendsResponse>> getTrends(
            @Parameter(description = "Period: day, week, month, year") @RequestParam(defaultValue = "month") String period
    ) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.buildTrends(period))
                .map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                        .body(response));
    }

    @PostMapping("/dashboard/invalidate-cache")
    @Operation(summary = "Invalidate dashboard cache", description = "Forces refresh of cached dashboard data")
    public Mono<ResponseEntity<Map<String, String>>> invalidateCache(
            @Parameter(description = "Specific period to invalidate, or all if not specified") @RequestParam(required = false) String period
    ) {
        if (period != null && !period.isBlank()) {
            adminDashboardService.invalidateCache(period);
        } else {
            adminDashboardService.invalidateCache();
        }
        return Mono.just(ResponseEntity.ok(Map.of("status", "cache_invalidated")));
    }
}
